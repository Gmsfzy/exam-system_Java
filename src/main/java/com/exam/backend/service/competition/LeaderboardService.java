package com.exam.backend.service.competition;

import com.exam.backend.domain.entity.User;
import com.exam.backend.domain.entity.competition.CompetitionParticipant;
import com.exam.backend.dto.CompetitionDto;
import com.exam.backend.repository.UserRepository;
import com.exam.backend.repository.competition.CompetitionParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 限时赛实时榜单 (v4.0)：得分↓ → 用时↑ → 开始时间↑；同分同名次；含答题中用户。
 */
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private static final int TOP_N = 100;

    private final CompetitionParticipantRepository participantRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CompetitionDto.LeaderboardResponse leaderboard(Long competitionId, Long currentUserId) {
        // findLeaderboard 已按 score DESC, usedTime ASC, startedAt ASC 排序
        List<CompetitionParticipant> ranked = participantRepository.findLeaderboard(competitionId);
        Map<Long, String> names = namesFor(ranked.stream()
                .map(CompetitionParticipant::getUserId).collect(Collectors.toSet()));

        List<CompetitionDto.LeaderboardEntry> entries = new ArrayList<>();
        CompetitionDto.LeaderboardEntry myEntry = null;
        Integer myRank = null;

        // 用于同分判定：得分相同则同名次（用时/开始时间仅用于展示排序，不拆分名次）
        Double prevScore = null;
        int prevRank = 0;
        for (int i = 0; i < ranked.size(); i++) {
            CompetitionParticipant p = ranked.get(i);
            int rank;
            if (prevScore != null && Double.compare(p.getScore(), prevScore) == 0) {
                rank = prevRank;
            } else {
                rank = i + 1;
            }
            prevScore = p.getScore();
            prevRank = rank;

            CompetitionDto.LeaderboardEntry e = new CompetitionDto.LeaderboardEntry(
                    rank, p.getUserId(), names.getOrDefault(p.getUserId(), "user" + p.getUserId()),
                    p.getScore(), p.getUsedTime());
            if (i < TOP_N) entries.add(e);
            if (p.getUserId().equals(currentUserId)) {
                myEntry = e;
                myRank = rank;
            }
        }
        return new CompetitionDto.LeaderboardResponse(entries, myRank, myEntry);
    }

    /** 某用户在竞赛榜单中的名次（同分同名次），无记录返回 null */
    @Transactional(readOnly = true)
    public Integer rankOf(Long competitionId, Long userId) {
        List<CompetitionParticipant> ranked = participantRepository.findLeaderboard(competitionId);
        Double prevScore = null;
        int prevRank = 0;
        for (int i = 0; i < ranked.size(); i++) {
            CompetitionParticipant p = ranked.get(i);
            int rank;
            if (prevScore != null && Double.compare(p.getScore(), prevScore) == 0) {
                rank = prevRank;
            } else {
                rank = i + 1;
            }
            prevScore = p.getScore();
            prevRank = rank;
            if (p.getUserId().equals(userId)) return rank;
        }
        return null;
    }

    /** Top N（推送用），N<=0 表示全部 */
    @Transactional(readOnly = true)
    public List<CompetitionDto.LeaderboardEntry> top(Long competitionId, int n) {
        CompetitionDto.LeaderboardResponse r = leaderboard(competitionId, null);
        if (n <= 0) return r.entries();
        return r.entries().stream().limit(n).toList();
    }

    private Map<Long, String> namesFor(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids.isEmpty()) return map;
        for (User u : userRepository.findAllById(ids)) map.put(u.getId(), u.getUsername());
        return map;
    }
}
