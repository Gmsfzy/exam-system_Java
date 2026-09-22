package com.exam.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ResultDto {

    public record ResultSummary(
            Long id, Long examId, String examTitle, Double score, Double totalScore,
            LocalDateTime submittedAt,
            // M6：多轮次/发布门控/申诉状态
            Integer attemptNo, Long sessionId, Boolean published, String reviewStatus) {}

    public record ResultDetail(
            Long id, Long examId, String examTitle, Long studentId, String studentName,
            Double score, Double totalScore, LocalDateTime submittedAt,
            String aiAnalysis, Boolean answersReleased, Boolean grading,
            List<ExamExecutionDto.AnswerView> answers,
            Integer attemptNo, Boolean published, String reviewStatus,
            String reviewReason, String reviewReply, LocalDateTime reviewedAt) {}

    /** M6 成绩申诉：学生发起 / 教师处理 */
    public record ReviewRequest(String reason) {}

    public record ReviewHandleRequest(@NotNull String action, Double newScore, String reply) {}

    public record ReviewResponse(Long resultId, String reviewStatus) {}

    public record ManualGradeRequest(
            @NotNull Long questionId, Double manualScore, String manualComment) {}

    public record ManualGradeResponse(Long answerId, Double manualScore, String manualComment) {}

    public record GradeStudentView(
            Long studentId, String studentName, String examTitle,
            Double score, Double totalScore, Boolean hasPendingManual,
            Integer attemptNo) {}

    /** M6 聚类批注：未批主观题按题目聚合 */
    public record ClusterItem(
            Long questionId, String content, com.exam.backend.domain.enums.QuestionTypeEnum type, Double maxScore,
            List<ClusterAnswer> answers) {}

    public record ClusterAnswer(
            Long answerId, Long studentId, String studentName,
            String studentAnswer, Double aiScore, String aiAnalysis) {}

    /** M6 同题批量给分 */
    public record BatchGradeRequest(@NotNull Long questionId, @NotNull Double score, String comment) {}

    public record BatchGradeResponse(Long questionId, Integer gradedCount) {}

    public record GradeableAnswerView(
            Long answerId, Long questionId, String content,
            com.exam.backend.domain.enums.QuestionTypeEnum type,
            String studentAnswer, String correctAnswer,
            Double aiScore, String aiAnalysis,
            Double manualScore, String manualComment) {}

    public record AnalysisResponse(
            Long examId, String examTitle,
            Integer studentCount, Integer submittedCount,
            Double averageScore, Double maxScore, Double minScore,
            Double passRate, Double passLine,
            List<Map<String, Object>> scoreDistribution,
            List<Map<String, Object>> typeAnalysis,
            List<Map<String, Object>> ranking) {}
}
