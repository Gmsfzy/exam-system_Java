package com.exam.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 征集悬赏 DTO (v4.0)，对齐文档 4.14。全部 record，字段 camelCase。
 */
public final class BountyDto {

    private BountyDto() {}

    // ==================== 请求 ====================

    public record BountyRequest(
            String bountyType,
            @NotBlank(message = "标题不能为空") String title,
            String description,
            Long targetQuestionId,
            Map<String, Object> targetQuestionSnapshot,
            Long majorId,
            String qType,
            String qDifficulty,
            Integer rewardPoints,
            LocalDateTime deadline
    ) {}

    public record SubmissionRequest(
            String content,
            String qContent,
            List<String> qOptions,
            String qAnswer,
            String qAnalysis,
            String qType,
            String qDifficulty,
            String qKnowledge
    ) {}

    public record RejectRequest(String reviewComment) {}

    // ==================== 视图 ====================

    /** 答案征集关联的题库题（answer/analysis 仅教师下发） */
    public record TargetQuestionView(
            Long id, String content, List<String> options, String answer, String analysis
    ) {}

    /** 悬赏广场列表项 */
    public record BountyListItem(
            Long id, String bountyType, String title, String description,
            Long publisherId, String publisherName, Long majorId, String majorName,
            String qType, String qDifficulty, Integer rewardPoints,
            String status, LocalDateTime deadline, LocalDateTime createdAt,
            Integer submissionCount, Integer pendingCount,
            Map<String, Object> snapshot, Boolean isPublisher
    ) {}

    /** 悬赏详情 */
    public record BountyDetailView(
            Long id, String bountyType, String title, String description,
            Long publisherId, String publisherName, Long majorId, String majorName,
            String qType, String qDifficulty, Integer rewardPoints,
            String status, LocalDateTime deadline, LocalDateTime createdAt, LocalDateTime closedAt,
            Integer submissionCount, Integer pendingCount,
            TargetQuestionView targetQuestion, Map<String, Object> snapshot,
            Boolean isPublisher, Long acceptedSubmissionId, SubmissionView mySubmission
    ) {}

    /** 投稿视图 */
    public record SubmissionView(
            Long id, Long bountyId, Long submitterId, String submitterName,
            String content, String qContent, List<String> qOptions, String qAnswer, String qAnalysis,
            String qType, String qDifficulty, String qKnowledge,
            String status, String reviewComment, LocalDateTime createdAt, LocalDateTime reviewedAt
    ) {}

    /** 我的投稿列表项 */
    public record MySubmissionItem(
            Long submissionId, Long bountyId, String bountyTitle, String bountyType,
            Integer rewardPoints, String submissionStatus, String bountyStatus,
            LocalDateTime submittedAt, LocalDateTime reviewedAt
    ) {}

    /** 采纳内部结果（供积分结算） */
    public record AcceptOutcome(
            Long submitterId, Integer rewardPoints, Long bountyId, Long acceptedQuestionId
    ) {}

    /** 采纳响应 */
    public record AcceptResponse(String message, Long acceptedQuestionId, Integer rewardPoints) {}
}
