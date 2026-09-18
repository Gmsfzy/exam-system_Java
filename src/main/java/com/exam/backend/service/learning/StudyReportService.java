package com.exam.backend.service.learning;

import com.exam.backend.domain.entity.learning.StudyLog;
import com.exam.backend.dto.LearningDto;
import com.exam.backend.repository.learning.StudyLogRepository;
import com.exam.backend.repository.learning.StudyPlanRepository;
import com.exam.backend.repository.learning.WrongAnswerRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 学习报告子模块 (v4.0 自学)：基于 StudyLog 的总览与每日聚合。
 */
@Service
@RequiredArgsConstructor
public class StudyReportService {

    private final StudyLogRepository studyLogRepository;
    private final WrongAnswerRecordRepository wrongAnswerRecordRepository;
    private final StudyPlanRepository studyPlanRepository;

    @Transactional(readOnly = true)
    public LearningDto.ReportOverview overview(Long userId) {
        List<StudyLog> logs = studyLogRepository.findByUserIdOrderByStudiedAtDesc(userId);
        int totalQuestions = logs.stream().mapToInt(StudyLog::getTotalQuestions).sum();
        int correctCount = logs.stream().mapToInt(StudyLog::getCorrectCount).sum();
        int totalTime = logs.stream().mapToInt(StudyLog::getTimeSpentSec).sum();
        long studyDays = logs.stream()
                .map(l -> l.getStudiedAt().toLocalDate())
                .distinct().count();
        long wrongTotal = wrongAnswerRecordRepository
                .findByUserIdOrderByLastWrongAtDesc(userId).size();
        long wrongPending = wrongAnswerRecordRepository.countByUserIdAndIsMasteredFalse(userId);
        long activePlans = studyPlanRepository.findByUserIdAndStatus(userId, "active").size();
        double accuracy = totalQuestions > 0 ? (double) correctCount / totalQuestions : 0.0;
        return new LearningDto.ReportOverview(
                totalQuestions, accuracy, totalTime,
                wrongPending, wrongTotal, activePlans, studyDays);
    }

    /** 每日学习报告：days 默认 7，返回区间内每日聚合（无记录的日期补零） */
    @Transactional(readOnly = true)
    public List<LearningDto.DailyReportItem> daily(Long userId, Integer days) {
        int n = days == null || days <= 0 ? 7 : Math.min(days, 90);
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(n - 1L);
        LocalDateTime fromTs = from.atStartOfDay();

        Map<LocalDate, List<StudyLog>> grouped = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            // 预置可变空列表，保证无记录日期补零
            grouped.put(d, new java.util.ArrayList<>());
        }
        studyLogRepository.findByUserIdAndStudiedAtGreaterThanEqualOrderByStudiedAtDesc(userId, fromTs)
                .forEach(log -> grouped
                        .computeIfAbsent(log.getStudiedAt().toLocalDate(), k -> new java.util.ArrayList<>()
                        ).add(log));

        return grouped.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> {
                    List<StudyLog> logs = e.getValue();
                    int total = logs.stream().mapToInt(StudyLog::getTotalQuestions).sum();
                    int correct = logs.stream().mapToInt(StudyLog::getCorrectCount).sum();
                    int time = logs.stream().mapToInt(StudyLog::getTimeSpentSec).sum();
                    double accuracy = total > 0 ? (double) correct / total : 0.0;
                    return new LearningDto.DailyReportItem(
                            e.getKey(), total, correct, accuracy, time,
                            (int) logs.stream().filter(l -> "exam".equals(l.getLogType())).count(),
                            (int) logs.stream().filter(l -> "practice".equals(l.getLogType())).count(),
                            (int) logs.stream().filter(l -> "competition".equals(l.getLogType())).count());
                }).toList();
    }
}
