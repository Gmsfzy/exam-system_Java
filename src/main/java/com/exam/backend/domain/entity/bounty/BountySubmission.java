package com.exam.backend.domain.entity.bounty;

import com.exam.backend.domain.converter.JsonStringListConverter;
import com.exam.backend.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 悬赏投稿 (v3.2/v4.0)：每人每悬赏限一条（唯一约束 bounty_id+submitter_id），
 * 题目投稿采纳后入库为正式 Question（{@code acceptedQuestionId}）。
 * status：{@code pending} / {@code accepted} / {@code rejected}。
 */
@Entity
@Table(name = "bounty_submission",
        uniqueConstraints = @UniqueConstraint(name = "uk_bounty_submitter",
                columnNames = {"bounty_id", "submitter_id"}),
        indexes = {
                @Index(name = "idx_bs_bounty", columnList = "bounty_id"),
                @Index(name = "idx_bs_submitter", columnList = "submitter_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BountySubmission extends BaseEntity {

    @Column(name = "bounty_id", nullable = false)
    private Long bountyId;

    @Column(name = "submitter_id", nullable = false)
    private Long submitterId;

    /** 答案投稿正文（答案/解析），答案投稿必填 */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 题目投稿：题干，必填 */
    @Column(name = "q_content", columnDefinition = "TEXT")
    private String qContent;

    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "q_options", columnDefinition = "TEXT")
    private List<String> qOptions;

    @Column(name = "q_answer", columnDefinition = "TEXT")
    private String qAnswer;

    @Column(name = "q_analysis", columnDefinition = "TEXT")
    private String qAnalysis;

    @Column(name = "q_type", length = 20)
    private String qType;

    @Column(name = "q_difficulty", length = 20)
    private String qDifficulty;

    @Column(name = "q_knowledge", length = 200)
    private String qKnowledge;

    /** pending / accepted / rejected */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "pending";

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    /** 题目投稿采纳后入库的正式题目 ID */
    @Column(name = "accepted_question_id")
    private Long acceptedQuestionId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
