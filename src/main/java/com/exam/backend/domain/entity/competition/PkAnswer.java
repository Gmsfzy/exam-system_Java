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
 * PK 答题记录 (v4.0)：唯一约束 (battle_id, player_id, cq_id)。
 */
@Entity
@Table(name = "pk_answer",
        uniqueConstraints = @UniqueConstraint(name = "uk_pk_answer", columnNames = {"battle_id", "player_id", "cq_id"}),
        indexes = @Index(name = "idx_pk_answer_battle", columnList = "battle_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PkAnswer extends BaseEntity {

    @Column(name = "battle_id", nullable = false)
    private Long battleId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

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
