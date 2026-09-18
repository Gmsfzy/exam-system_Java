package com.exam.backend.service;

import com.exam.backend.repository.AnswerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 主观题判分补偿扫描器 (v4.0)。
 * <p>兜底两类情况：① 交卷进程提交后、异步任务尚未执行前 JVM 重启，pending_ai 永远没人处理；
 * ② 异步工作者中途崩溃，题目卡在 grading。二者都会在超过卡死阈值后被重新抢占判分，
 * 保证「交卷成功 ⇒ 主观题最终一定被处理（graded 或转人工 failed）」，不丢单。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectiveGradingSweeper {

    private static final int BATCH = 50;

    private final AnswerRepository answerRepository;
    private final SubjectiveGradingService gradingService;

    @Scheduled(fixedRate = 90_000L, initialDelay = 45_000L)
    public void rescueStuckGrading() {
        try {
            LocalDateTime staleBefore = LocalDateTime.now()
                    .minusMinutes(SubjectiveGradingService.GRADING_STALE_MINUTES);
            List<Long> sessions = answerRepository.findSessionsNeedingGrading(
                    staleBefore, PageRequest.of(0, BATCH));
            if (sessions.isEmpty()) return;
            log.info("subjective grading sweeper re-dispatching {} session(s)", sessions.size());
            for (Long sessionId : sessions) {
                gradingService.gradeSessionAsync(sessionId);
            }
        } catch (Exception e) {
            log.error("subjective grading sweeper error: {}", e.getMessage(), e);
        }
    }
}
