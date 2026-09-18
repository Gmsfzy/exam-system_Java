package com.exam.backend.service.learning;

import com.exam.backend.domain.entity.learning.StudyLog;
import com.exam.backend.repository.learning.StudyLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 学习日志聚合写入 (v4.0 自学)：考试/练习/竞赛结束后调用，供学习报告查询。
 * logType 硬编码：exam / competition / practice。
 */
@Service
@RequiredArgsConstructor
public class StudyLogService {

    private final StudyLogRepository studyLogRepository;

    @Transactional
    public void writeExamLog(Long userId, Long examId, int totalQuestions, int correctCount,
                             double score, double totalScore, LocalDateTime startTime, LocalDateTime endTime) {
        int timeSpent = calcTimeSec(startTime, endTime);
        double ratio = totalScore > 0 ? Math.min(1.0, score / totalScore) : 0.0;
        save(userId, "exam", examId, totalQuestions, correctCount, ratio, timeSpent);
    }

    @Transactional
    public void writePracticeLog(Long userId, Long sessionId, int totalQuestions, int correctCount,
                                 LocalDateTime startTime, LocalDateTime endTime) {
        int timeSpent = calcTimeSec(startTime, endTime);
        double ratio = totalQuestions > 0 ? (double) correctCount / totalQuestions : 0.0;
        save(userId, "practice", sessionId, totalQuestions, correctCount, ratio, timeSpent);
    }

    private void save(Long userId, String logType, Long referenceId,
                      int totalQuestions, int correctCount, double ratio, int timeSpent) {
        studyLogRepository.save(StudyLog.builder()
                .userId(userId)
                .logType(logType)
                .referenceId(referenceId)
                .totalQuestions(totalQuestions)
                .correctCount(correctCount)
                .scoreRatio(ratio)
                .timeSpentSec(timeSpent)
                .studiedAt(LocalDateTime.now())
                .build());
    }

    private int calcTimeSec(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) return 0;
        return (int) Duration.between(start, end).getSeconds();
    }
}
