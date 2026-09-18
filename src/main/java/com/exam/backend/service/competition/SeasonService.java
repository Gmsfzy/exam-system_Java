package com.exam.backend.service.competition;

import com.exam.backend.domain.entity.User;
import com.exam.backend.domain.entity.competition.SeasonArchive;
import com.exam.backend.domain.entity.competition.SeasonMeta;
import com.exam.backend.domain.entity.competition.UserPointsProfile;
import com.exam.backend.dto.CompetitionDto;
import com.exam.backend.repository.UserRepository;
import com.exam.backend.repository.competition.SeasonArchiveRepository;
import com.exam.backend.repository.competition.SeasonMetaRepository;
import com.exam.backend.repository.competition.UserPointsProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 赛季服务 (v4.0)：自然月赛季 'YYYY-MM'，惰性归档过完的月份（SeasonMeta CAS 抢锁），
 * 归档快照存 SeasonArchive 并给 Top3 发「季度巅峰」勋章。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonService {

    private final SeasonMetaRepository seasonMetaRepository;
    private final SeasonArchiveRepository seasonArchiveRepository;
    private final UserPointsProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final GamificationService gamificationService;

    @Transactional
    public void ensureSeason(String season) {
        if (seasonMetaRepository.findBySeason(season).isEmpty()) {
            seasonMetaRepository.save(SeasonMeta.builder().season(season).archived(false).build());
        }
    }

    /**
     * 惰性归档：把所有早于当前月且未归档的赛季快照落库。由 /rank/me 入口或调度触发。
     * 全程 try/catch，单个赛季归档失败不影响其它。
     */
    @Transactional
    public void lazyArchivePreviousSeasons() {
        String current = gamificationService.currentSeason();
        ensureSeason(current);
        List<SeasonMeta> metas = seasonMetaRepository.findAll();
        for (SeasonMeta m : metas) {
            if (Boolean.TRUE.equals(m.getArchived())) continue;
            if (m.getSeason().compareTo(current) >= 0) continue; // 当前及未来月不归档
            try {
                archiveSeason(m.getSeason());
            } catch (Exception e) {
                log.error("archive season {} failed: {}", m.getSeason(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void archiveSeason(String season) {
        // CAS 抢锁：只有把 archived 从 false 翻成 true 的线程才继续
        if (seasonMetaRepository.findBySeason(season).isEmpty()) {
            seasonMetaRepository.save(SeasonMeta.builder().season(season).archived(false).build());
        }
        int claimed = seasonMetaRepository.claimArchive(season, LocalDateTime.now());
        if (claimed != 1) return;

        List<UserPointsProfile> ranked = rankProfiles(profileRepository.findBySeason(season));
        List<SeasonArchive> snapshots = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            UserPointsProfile p = ranked.get(i);
            int rank = i + 1;
            snapshots.add(SeasonArchive.builder()
                    .season(season).userId(p.getUserId()).rank(rank)
                    .points(p.getPoints())
                    .tier(gamificationService.tierOf(p.getPoints()).name())
                    .pkWin(p.getPkWin())
                    .pkTotal(p.getPkWin() + p.getPkLose() + p.getPkDraw())
                    .timedFinished(p.getTimedFinished())
                    .build());
        }
        seasonArchiveRepository.saveAll(snapshots);
        // Top3 勋章
        for (int i = 0; i < Math.min(3, ranked.size()); i++) {
            gamificationService.grantBadge(ranked.get(i).getUserId(),
                    GamificationService.BADGE_SEASON_TOP, season, null);
        }
        log.info("season {} archived, snapshots={}", season, snapshots.size());
    }

    /** 排序 + 同分同名次：得分↓、达成靠前者(id asc) */
    private List<UserPointsProfile> rankProfiles(List<UserPointsProfile> in) {
        return in.stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(b.getPoints(), a.getPoints());
                    if (c != 0) return c;
                    return Long.compare(a.getId(), b.getId());
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<String> listSeasons() {
        String current = gamificationService.currentSeason();
        List<String> seasons = new ArrayList<>();
        seasons.add(current);
        seasonMetaRepository.findByArchivedTrueOrderBySeasonDesc()
                .forEach(m -> { if (!m.getSeason().equals(current)) seasons.add(m.getSeason()); });
        return seasons;
    }

    @Transactional(readOnly = true)
    public CompetitionDto.SeasonRankView seasonRank(String season, Long currentUserId) {
        List<UserPointsProfile> ranked = rankProfiles(profileRepository.findBySeason(season));
        Map<Long, String> names = namesFor(ranked.stream().map(UserPointsProfile::getUserId).collect(Collectors.toSet()));

        List<CompetitionDto.SeasonRankEntry> entries = new ArrayList<>();
        Integer myRank = null;
        int prevPoints = Integer.MIN_VALUE;
        int prevRank = 0;
        for (int i = 0; i < ranked.size(); i++) {
            UserPointsProfile p = ranked.get(i);
            int rank = (p.getPoints() == prevPoints) ? prevRank : (i + 1);
            prevPoints = p.getPoints();
            prevRank = rank;
            if (i < 100) {
                entries.add(new CompetitionDto.SeasonRankEntry(rank, p.getUserId(),
                        names.getOrDefault(p.getUserId(), "user" + p.getUserId()),
                        p.getPoints(), gamificationService.tierOf(p.getPoints()).name(),
                        p.getPkWin(), p.getTimedFinished()));
            }
            if (p.getUserId().equals(currentUserId)) myRank = rank;
        }
        return new CompetitionDto.SeasonRankView(season, entries, myRank);
    }

    @Transactional(readOnly = true)
    public List<CompetitionDto.ArchiveView> myArchives(Long userId) {
        return seasonArchiveRepository.findByUserIdOrderBySeasonDesc(userId).stream()
                .map(a -> new CompetitionDto.ArchiveView(a.getSeason(), a.getRank(), a.getPoints(),
                        a.getTier(), a.getPkWin(), a.getPkTotal(), a.getTimedFinished()))
                .toList();
    }

    /** 当前赛季内该用户名次（同分同名次） */
    @Transactional(readOnly = true)
    public Integer seasonRankOf(Long userId, String season) {
        List<UserPointsProfile> ranked = rankProfiles(profileRepository.findBySeason(season));
        int prevPoints = Integer.MIN_VALUE;
        int prevRank = 0;
        for (int i = 0; i < ranked.size(); i++) {
            UserPointsProfile p = ranked.get(i);
            int rank = (p.getPoints() == prevPoints) ? prevRank : (i + 1);
            prevPoints = p.getPoints();
            prevRank = rank;
            if (p.getUserId().equals(userId)) return rank;
        }
        return null;
    }

    private Map<Long, String> namesFor(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids.isEmpty()) return map;
        for (User u : userRepository.findAllById(ids)) map.put(u.getId(), u.getUsername());
        return map;
    }
}
