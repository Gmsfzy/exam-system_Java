package com.exam.backend.dto;

import com.exam.backend.domain.enums.SessionStatusEnum;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class ExamExecutionDto {

    public record StartExamResponse(Long sessionId, Long examId, Long studentId,
                                       LocalDateTime startTime, SessionStatusEnum status) {}

    /** 答题界面题目视图 - 不含 answer/analysis；M6 附带材料题共享材料 */
    public record TakeQuestionView(
            Long questionId, String content, List<String> options,
            com.exam.backend.domain.enums.QuestionTypeEnum type,
            com.exam.backend.domain.enums.DifficultyEnum difficulty,
            Integer score, Integer order,
            String studentAnswer, Boolean isAnswered,
            String material, String materialGroup) {}

    public record TakeExamResponse(
            Long sessionId, Long examId, String title,
            LocalDateTime startTime, LocalDateTime endTime, Integer duration,
            List<TakeQuestionView> questions) {}

    public record SaveAnswerRequest(
            @NotNull Long questionId, String studentAnswer) {}

    public record SaveAnswerResponse(Long answerId, Long questionId, Boolean saved) {}

    public record SubmitResponse(
            Long sessionId, Long resultId, Double score, Double totalScore,
            LocalDateTime submittedAt, SessionStatusEnum status, Boolean grading,
            Integer attemptNo, Boolean published) {}

    public record ReportSwitchResponse(Integer switchCount, Boolean autoSubmitted) {}

    public record ExamReportResponse(
            Long sessionId, Long resultId, Double score, Double totalScore,
            SessionStatusEnum status, Boolean grading, List<AnswerView> answers,
            Integer attemptNo, Boolean published) {}

    public record AnswerView(
            Long questionId, String content,
            com.exam.backend.domain.enums.QuestionTypeEnum type,
            String studentAnswer, String correctAnswer, String analysis,
            Boolean isCorrect, Double score, Double maxScore,
            Boolean needsManualGrade, String gradeStatus,
            String material, String materialGroup) {}
}
