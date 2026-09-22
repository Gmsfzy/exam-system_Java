package com.exam.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "result")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Result extends BaseEntity {

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "score")
    private Double score;

    @Column(name = "total_score")
    private Double totalScore;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "ai_analysis", columnDefinition = "TEXT")
    private String aiAnalysis;

    /** 是否仍有主观题处于 AI 异步评分中（v4.0）；为 true 时前端展示“AI 评分中” */
    @Column(name = "grading")
    @Builder.Default
    private Boolean grading = false;

    // ==== M6 成绩归属与申诉字段 ====

    /** 成绩来源会话（多轮次时对应最新/最优一轮） */
    @Column(name = "session_id")
    private Long sessionId;

    /** 申诉状态：none / pending / approved / rejected */
    @Column(name = "review_status", length = 16)
    @Builder.Default
    private String reviewStatus = Result.REVIEW_NONE;

    @Column(name = "review_reason", columnDefinition = "TEXT")
    private String reviewReason;

    @Column(name = "review_reply", columnDefinition = "TEXT")
    private String reviewReply;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public static final String REVIEW_NONE = "none";
    public static final String REVIEW_PENDING = "pending";
    public static final String REVIEW_APPROVED = "approved";
    public static final String REVIEW_REJECTED = "rejected";

    public String reviewStatusEffective() {
        return reviewStatus == null ? REVIEW_NONE : reviewStatus;
    }
}
