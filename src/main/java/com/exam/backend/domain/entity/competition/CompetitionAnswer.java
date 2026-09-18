package com.exam.backend.domain.entity.competition;

import com.exam.backend.domain.entity.BaseEntity;
import jakarta.persistence.Column;
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

/**
 * 限时赛答题记录 (v4.0)：唯一约束 (participant_id, cq_id) 防重复提交刷分。
 */
@Entity
@Table(name = "competition_answer",
        uniqueConstraints = @UniqueConstraint(name = "uk_comp_answer", columnNames = {"participant_id", "cq_id"}),
        indexes = @Index(name = "idx_comp_answer_part", columnList = "participant_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompetitionAnswer extends BaseEntity {

    @Column(name = "participant_id", nullable = false)
    private Long participantId;

    @Column(name = "cq_id", nullable = false)
    private Long cqId;

    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Column(name = "is_correct", nullable = false)
    @Builder.Default
    private Boolean isCorrect = false;

    @Column(name = "gained_score", nullable = false)
    @Builder.Default
    private Double gainedScore = 0.0;

    @Column(name = "time_spent", nullable = false)
    @Builder.Default
    private Integer timeSpent = 0;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;
}
