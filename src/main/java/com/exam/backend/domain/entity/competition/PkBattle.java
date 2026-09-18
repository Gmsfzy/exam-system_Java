package com.exam.backend.domain.entity.competition;

import com.exam.backend.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 1v1 PK 对战 (v4.0)：status waiting → playing → finished / cancelled。
 * waiting 30 分钟未接受自动 cancelled；结算用 CAS (UPDATE ... WHERE status='playing') 抢结算权。
 */
@Entity
@Table(name = "pk_battle",
        indexes = {
                @Index(name = "idx_pk_battle_comp", columnList = "competition_id"),
                @Index(name = "idx_pk_battle_challenger", columnList = "challenger_id"),
                @Index(name = "idx_pk_battle_opponent", columnList = "opponent_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PkBattle extends BaseEntity {

    @Column(name = "competition_id", nullable = false)
    private Long competitionId;

    @Column(name = "challenger_id", nullable = false)
    private Long challengerId;

    @Column(name = "opponent_id")
    private Long opponentId;

    /** waiting / playing / finished / cancelled */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "waiting";

    @Column(name = "winner_id")
    private Long winnerId;

    @Column(name = "challenger_score", nullable = false)
    @Builder.Default
    private Double challengerScore = 0.0;

    @Column(name = "opponent_score", nullable = false)
    @Builder.Default
    private Double opponentScore = 0.0;

    @Column(name = "challenger_answered", nullable = false)
    @Builder.Default
    private Integer challengerAnswered = 0;

    @Column(name = "opponent_answered", nullable = false)
    @Builder.Default
    private Integer opponentAnswered = 0;

    @Column(name = "challenger_finished_at")
    private LocalDateTime challengerFinishedAt;

    @Column(name = "opponent_finished_at")
    private LocalDateTime opponentFinishedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
