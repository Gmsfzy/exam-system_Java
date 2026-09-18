package com.exam.backend.dto;

import com.exam.backend.domain.enums.ExamStatusEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class ExamDto {

    public record ExamRequest(
            @NotBlank(message = "考试标题不能为空") String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer duration) {}

    public record ExamResponse(
            Long id, String title, String description,
            LocalDateTime startTime, LocalDateTime endTime, Integer duration,
            ExamStatusEnum status, Long creatorId,
            String invitationCode, String invitationUrl,
            LocalDateTime createdAt) {}

    public record AddQuestionsRequest(List<Long> questionIds) {}

    public record RemoveQuestionResponse(Long examId, Long questionId, Boolean removed) {}

    public record InvitationResponse(String code, String url, String qrDataUrl) {}

    public record SmartCompositionRequest(
            @NotNull Long majorId,
            com.exam.backend.domain.enums.QuestionTypeEnum type,
            com.exam.backend.domain.enums.DifficultyEnum difficulty,
            String sourceFilter, // mine/public/all
            Integer count) {}

    public record SmartCompositionResponse(Long examId, Integer added, List<Long> questionIds) {}

    public record InviteRequest(List<Long> studentIds) {}

    public record InviteResponse(Long examId, Integer invited, List<Long> existingIds) {}

    public record RemoveStudentResponse(Long examId, Long studentId, Boolean removed) {}

    public record JoinRequest(String code) {}

    public record JoinResponse(Long examId, String title, Long studentId) {}

    public record StudentBriefResponse(Long id, String username, String email) {}

    public record ExamQuestionView(
            Long questionId, String content, List<String> options,
            com.exam.backend.domain.enums.QuestionTypeEnum type,
            com.exam.backend.domain.enums.DifficultyEnum difficulty,
            Integer score, Integer order) {}
}
