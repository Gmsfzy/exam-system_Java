package com.exam.backend.service.bounty;

import com.exam.backend.service.competition.GamificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 悬赏采纳奖励结算 (v4.0)：给投稿人加竞技积分 + 「悬赏新星」勋章 + STOMP 推送。
 * 独立事务，由 {@link BountyService#accept} 提交后调用；失败仅记日志，不回滚采纳结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BountyRewardService {

    private final GamificationService gamificationService;

    @Transactional
    public int grant(Long submitterId, int rewardPoints, Long bountyId) {
        int delta = gamificationService.awardBountyPoints(submitterId, bountyId, rewardPoints);
        log.info("bounty reward granted submitter={} bounty={} points=+{}", submitterId, bountyId, delta);
        return delta;
    }
}
