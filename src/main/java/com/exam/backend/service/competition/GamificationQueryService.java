package com.exam.backend.service.competition;

import com.exam.backend.domain.entity.User;
import com.exam.backend.domain.entity.competition.UserBadge;
import com.exam.backend.domain.entity.competition.UserPointsProfile;
import com.exam.backend.dto.CompetitionDto;
import com.exam.backend.repository.UserRepository;
import com.exam.backend.repository.competition.UserBadgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 竞技化查询门面 (v4.0)：我的段位 / 勋章墙。写侧计分在 GamificationService，赛季榜/归档在 SeasonService。
 */
@Service
@RequiredArgsConstructor
public class GamificationQueryService {

    private final GamificationService gamificationService;
    private final SeasonService seasonService;
    private final UserBadgeRepository badgeRepository;
    private final UserRepository userRepository;

    @Transactional
    public CompetitionDto.RankMeView rankMe(Long userId) {
        // 入口触发惰性赛季归档（过完的自然月快照落库）
        seasonService.lazyArchivePreviousSeasons();
        String season = gamificationService.currentSeason();
        UserPointsProfile p = gamificationService.ensureProfile(userId, season);
        GamificationService.Tier tier = gamificationService.tierOf(p.getPoints());
        GamificationService.Tier next = gamificationService.nextTierOf(p.getPoints());
        int pkTotal = p.getPkWin() + p.getPkLose() + p.getPkDraw();
        double winRate = pkTotal > 0 ? round1((double) p.getPkWin() / pkTotal * 100) : 0.0;
        Integer progress = next == null ? null : Math.max(0, next.min() - p.getPoints());
        Integer myRank = seasonService.seasonRankOf(userId, season);
        return new CompetitionDto.RankMeView(season, p.getPoints(), tier.name(), tier.icon(),
                next == null ? null : next.name(), next == null ? null : next.min(), progress,
                p.getPkWin(), p.getPkLose(), p.getPkDraw(), winRate,
                p.getStreak(), p.getMaxStreak(), p.getTimedFinished(), myRank);
    }

    @Transactional(readOnly = true)
    public List<CompetitionDto.BadgeView> badges(Long userId) {
        List<UserBadge> owned = badgeRepository.findByUserIdOrderByGrantedAtDesc(userId);
        Map<String, UserBadge> latest = new HashMap<>();
        for (UserBadge b : owned) latest.putIfAbsent(b.getBadgeCode(), b); // grantedAt desc → 保留最新

        List<CompetitionDto.BadgeView> views = new ArrayList<>();
        GamificationService.badgeCatalog().forEach((code, def) -> {
            UserBadge b = latest.get(code);
            views.add(new CompetitionDto.BadgeView(code, def[0], def[1], def[2],
                    b == null ? null : b.getSeason(), b != null, b == null ? null : b.getGrantedAt()));
        });
        return views;
    }

    @Transactional(readOnly = true)
    public String usernameOf(Long userId) {
        return userRepository.findById(userId).map(User::getUsername).orElse("user" + userId);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
