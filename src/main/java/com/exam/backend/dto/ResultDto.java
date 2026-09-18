package com.exam.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ResultDto {

    public record ResultSummary(
            Long id, Long examId, String examTitle, Double score, Double totalScore,
            LocalDateTime submittedAt) {}

    public record ResultDetail(
            Long id, Long examId, String examTitle, Long studentId, String studentName,
            Double score, Double totalScore, LocalDateTime submittedAt,
            String aiAnalysis, Boolean answersReleased, Boolean grading,
            List<ExamExecutionDto.AnswerView> answers) {}

    public record ManualGradeRequest(
            @NotNull Long questionId, Double manualScore, String manualComment) {}

    public record ManualGradeResponse(Long answerId, Double manualScore, String manualComment) {}

    public record GradeStudentView(
            Long studentId, String studentName, String examTitle,
            Double score, Double totalScore, Boolean hasPendingManual) {}

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
