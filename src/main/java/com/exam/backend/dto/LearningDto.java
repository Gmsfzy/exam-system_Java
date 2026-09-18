package com.exam.backend.dto;

import com.exam.backend.domain.enums.DifficultyEnum;
import com.exam.backend.domain.enums.QuestionTypeEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 自主学习模块 DTO (v4.0)。字段命名沿用现有 camelCase 约定。
 */
public class LearningDto {

    // ==================== 错题本 ====================

    /** 错题本列表项：带题目快照信息（题库题目被删时题目字段为 null） */
    public record WrongRecordView(
            Long id,
            Long questionId,
            String sourceType,
            Long sourceId,
            String wrongAnswer,
            String correctAnswer,
            Boolean isMastered,
            Integer wrongCount,
            LocalDateTime lastWrongAt,
            LocalDateTime masteredAt,
            String questionContent,
            List<String> options,
            QuestionTypeEnum questionType,
            DifficultyEnum difficulty,
            String knowledge,
            String analysis) {}

    // ==================== 自由刷题 ====================

    public record PracticeStartRequest(
            Long majorId,
            Long courseId,
            Long chapterId,
            @Min(1) @Max(50) Integer count,   // 默认 10
            String title) {}

    public record PracticeStartResponse(
            Long sessionId,
            String status,
            Integer questionsCount,
            List<PracticeQuestionView> questions) {}

    /** 练习题目视图：不下发 answer/analysis，判分由服务端完成 */
    public record PracticeQuestionView(
            Long questionId,
            String content,
            List<String> options,
            QuestionTypeEnum type,
            DifficultyEnum difficulty,
            String knowledge,
            String studentAnswer,
            Boolean isCorrect) {}

    public record PracticeDetailView(
            Long sessionId,
            String title,
            String status,
            Integer questionsCount,
            Integer correctCount,
            Integer totalTimeSec,
            LocalDateTime startTime,
            LocalDateTime endTime,
            List<PracticeQuestionView> questions) {}

    public record PracticeAnswerRequest(
            @NotNull Long questionId,
            String studentAnswer,
            @Min(0) Integer timeSpentSec) {}

    /** 单题作答即时反馈（练习场景直接下发答案与解析） */
    public record PracticeAnswerResponse(
            Long questionId,
            Boolean isCorrect,
            String correctAnswer,
            String analysis) {}

    public record PracticeSubmitResponse(
            Long sessionId,
            Integer questionsCount,
            Integer answeredCount,
            Integer correctCount,
            Double accuracy,
            Integer totalTimeSec) {}

    public record PracticeHistoryItem(
            Long sessionId,
            String title,
            String status,
            Integer questionsCount,
            Integer correctCount,
            Integer totalTimeSec,
            LocalDateTime startTime,
            LocalDateTime endTime) {}

    // ==================== 学习计划 ====================

    public record PlanRequest(
            @NotBlank String title,
            String description,
            @Min(1) Integer targetCount,
            Long majorId,
            Long courseId,
            Long chapterId,
            LocalDate startDate,
            LocalDate endDate) {}

    public record PlanView(
            Long id,
            String title,
            String description,
            Integer targetCount,
            Integer completedCount,
            Long majorId,
            Long courseId,
            Long chapterId,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            LocalDateTime createdAt) {}

    // ==================== 学习报告 ====================

    public record ReportOverview(
            Integer totalQuestions,      // 累计学习题数
            Double accuracy,             // 综合正确率 0~1
            Integer totalTimeSec,        // 累计学习时长（秒）
            Long wrongPendingCount,      // 未掌握错题数
            Long wrongTotalCount,        // 错题总数
            Long activePlanCount,        // 进行中计划数
            Long studyDays) {}           // 有学习记录的天数

    public record DailyReportItem(
            LocalDate date,
            Integer totalQuestions,
            Integer correctCount,
            Double accuracy,
            Integer timeSpentSec,
            Integer examCount,
            Integer practiceCount,
            Integer competitionCount) {}

    // ==================== AI 答疑 ====================

    public record AskRequest(
            Long questionId,             // 可选：结合具体题目答疑
            @NotBlank String question) {}

    public record AskResponse(
            String answer) {}
}
