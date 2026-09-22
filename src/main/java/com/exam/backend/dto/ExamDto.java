package com.exam.backend.dto;

import com.exam.backend.domain.enums.ExamStatusEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ExamDto {

    public record ExamRequest(
            @NotBlank(message = "考试标题不能为空") String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer duration,
            // M6 考务配置（均可缺省，缺省保持存量行为）
            Boolean resultsPublished,
            Integer maxAttempts,
            com.exam.backend.domain.enums.ScoreStrategyEnum scoreStrategy,
            com.exam.backend.domain.enums.PaperModeEnum paperMode,
            Integer randomCount,
            Boolean shuffleOptions,
            com.exam.backend.domain.enums.MultiScoreRuleEnum multiScoreRule,
            Boolean anonymousGrading) {}

    public record ExamResponse(
            Long id, String title, String description,
            LocalDateTime startTime, LocalDateTime endTime, Integer duration,
            ExamStatusEnum status, Long creatorId,
            String invitationCode, String invitationUrl,
            LocalDateTime createdAt,
            // M6 考务配置回显（经实体 Effective 方法兜底，存量数据不会为 null）
            Boolean resultsPublished, Integer maxAttempts,
            com.exam.backend.domain.enums.ScoreStrategyEnum scoreStrategy,
            com.exam.backend.domain.enums.PaperModeEnum paperMode,
            Integer randomCount, Boolean shuffleOptions,
            com.exam.backend.domain.enums.MultiScoreRuleEnum multiScoreRule,
            Boolean anonymousGrading) {}

    public record PublishResultsResponse(Long examId, Boolean resultsPublished, Integer notified) {}

    public record GrantAttemptRequest(@NotNull Long studentId, Integer extraAttempts) {}

    public record GrantAttemptResponse(Long examId, Long studentId, Integer maxAttempts) {}

    /** M6 实时监考逐人视图（轮询友好） */
    public record MonitorRow(
            Long studentId, String studentName, Integer attemptNo,
            String status, Integer answeredCount, Integer totalCount,
            Integer switchCount, Double sessionScore, LocalDateTime startTime) {}

    /** M6 试题分析逐题视图：得分率/区分度/干扰项选择率 */
    public record ItemAnalysisRow(
            Long questionId, Integer order, String content, String type,
            Double maxScore, Integer answerCount,
            Double scoreRate, Double discrimination,
            List<Map<String, Object>> distractors) {}

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
