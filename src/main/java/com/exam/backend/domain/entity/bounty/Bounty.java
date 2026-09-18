package com.exam.backend.domain.entity.bounty;

import com.exam.backend.domain.converter.JsonMapConverter;
import com.exam.backend.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 悬赏单 (v3.2/v4.0)：师生均可发布，一条悬赏对应多条投稿，采纳一份后关闭。
 * <p>bountyType：{@code question} 题目征集 / {@code answer} 答案征集。
 * status：{@code open} 征集中 / {@code closed} 已采纳关闭；{@code expired} 不落库，
 * 由读取时按 deadline 惰性反映（见 BountyService#effectiveStatus）。</p>
 * <p>{@code acceptedSubmissionId} 用普通列（无 FK）避免与 bounty_submission 循环外键。</p>
 */
@Entity
@Table(name = "bounty")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Bounty extends BaseEntity {

    @Column(name = "publisher_id", nullable = false)
    private Long publisherId;

    @Column(name = "bounty_type", nullable = false, length = 20)
    @Builder.Default
    private String bountyType = "question";

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 答案征集：关联题库已有题（与 targetQuestionSnapshot 二选一） */
    @Column(name = "target_question_id")
    private Long targetQuestionId;

    /** 答案征集：自带题目快照 {content, options, answer}，answer 仅发布者可见 */
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "target_question_snapshot", columnDefinition = "TEXT")
    private Map<String, Object> targetQuestionSnapshot;

    /** 题目征集：期望专业（题目投稿采纳入库时使用） */
    @Column(name = "major_id")
    private Long majorId;

    @Column(name = "q_type", length = 20)
    private String qType;

    @Column(name = "q_difficulty", length = 20)
    private String qDifficulty;

    @Column(name = "reward_points", nullable = false)
    @Builder.Default
    private Integer rewardPoints = 10;

    /** open / closed（expired 惰性反映，不落库） */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "open";

    @Column(name = "deadline")
    private LocalDateTime deadline;

    /** 被采纳的投稿 ID；采纳时 CAS 写入，无 FK */
    @Column(name = "accepted_submission_id")
    private Long acceptedSubmissionId;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
