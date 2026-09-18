package com.exam.backend.service.competition;

import com.exam.backend.domain.entity.competition.Competition;
import com.exam.backend.repository.competition.CompetitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 竞赛后台调度 (v4.0)：每 60s 推进到期竞赛并结算积分、惰性归档过完的赛季。
 * 每任务独立 try/catch，失败不影响下一周期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitionSchedulerService {

    private final CompetitionRepository competitionRepository;
    private final CompetitionService competitionService;
    private final SeasonService seasonService;

    @Scheduled(fixedRate = 60_000L)
    public void settleExpiredCompetitions() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Competition> expiring = new ArrayList<>();
            expiring.addAll(competitionRepository.findByStatusAndEndTimeBefore("published", now));
            expiring.addAll(competitionRepository.findByStatusAndEndTimeBefore("ongoing", now));
            for (Competition c : expiring) {
                try {
                    c.setStatus("ended");
                    competitionRepository.save(c);
                    competitionService.settleCompetitionPoints(c.getId());
                } catch (Exception e) {
                    log.error("settle competition {} failed: {}", c.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("settleExpiredCompetitions error: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedRate = 300_000L)
    public void archiveAndExpire() {
        try {
            seasonService.lazyArchivePreviousSeasons();
        } catch (Exception e) {
            log.error("season archive error: {}", e.getMessage(), e);
        }
    }
}
