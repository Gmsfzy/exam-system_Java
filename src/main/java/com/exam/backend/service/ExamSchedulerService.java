package com.exam.backend.service;

import com.exam.backend.domain.entity.Exam;
import com.exam.backend.domain.entity.ExamSession;
import com.exam.backend.domain.enums.ExamStatusEnum;
import com.exam.backend.domain.enums.SessionStatusEnum;
import com.exam.backend.repository.ExamRepository;
import com.exam.backend.repository.ExamSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台调度: 自动结束过期 published 考试
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamSchedulerService {

    private final ExamRepository examRepository;
    private final ExamSessionRepository examSessionRepository;
    private final ExamExecutionService examExecutionService;

    @Scheduled(fixedRate = 60_000L)
    public void autoEndExpiredExams() {
        try {
            List<Exam> expired = examRepository.findByStatusAndEndTimeBefore(
                    ExamStatusEnum.published, LocalDateTime.now());
            for (Exam e : expired) {
                try {
                    autoEndOne(e);
                } catch (Exception ex) {
                    log.error("autoEnd exam {} failed: {}", e.getId(), ex.getMessage(), ex);
                }
            }
        } catch (Exception e) {
            log.error("autoEndExpiredExams scheduler error: {}", e.getMessage(), e);
        }
    }

    /**
     * 结束单个过期考试（C 瘦身：不开长事务）。考试置为 ended 单独提交；
     * 每个会话的 finalizeSubmission 各自走独立短事务（内部仅标 pending_ai + 发事件，AI 已移出事务）。
     * 逐会话 try/catch 隔离失败，避免会话数多时长时间持有写锁。
     */
    public void autoEndOne(Exam exam) {
        // 标记考试结束
        exam.setStatus(ExamStatusEnum.ended);
        examRepository.save(exam);

        // 自动提交所有未提交的会话
        List<ExamSession> active = examSessionRepository
                .findByExamIdAndStatus(exam.getId(), SessionStatusEnum.in_progress);
        for (ExamSession s : active) {
            try {
                examExecutionService.finalizeSubmission(
                        s.getExamId(), s.getStudentId(), s, SessionStatusEnum.auto_submitted);
            } catch (Exception ex) {
                log.error("auto submit session {} failed: {}", s.getId(), ex.getMessage(), ex);
            }
        }
        log.info("autoEnd exam {} (sessions submitted={})", exam.getId(), active.size());
    }
}