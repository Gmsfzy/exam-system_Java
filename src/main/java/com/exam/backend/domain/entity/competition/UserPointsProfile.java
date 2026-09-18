package com.exam.backend.domain.entity.competition;

import com.exam.backend.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 赛季积分档案 (v4.0)：唯一约束 (user_id, season)，season 格式 'YYYY-MM' 自然月。
 * 段位不入库，由 points 实时推导（见 GamificationService.tierOf）。
 */
@Entity
@Table(name = "user_points_profile",
        uniqueConstraints = @UniqueConstraint(name = "uk_points_user_season", columnNames = {"user_id", "season"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserPointsProfile extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 10)
    private String season;

    @Column(nullable = false)
    @Builder.Default
    private Integer points = 0;

    @Column(name = "pk_win", nullable = false)
    @Builder.Default
    private Integer pkWin = 0;

    @Column(name = "pk_lose", nullable = false)
    @Builder.Default
    private Integer pkLose = 0;

    @Column(name = "pk_draw", nullable = false)
    @Builder.Default
    private Integer pkDraw = 0;

    /** 当前连胜 */
    @Column(nullable = false)
    @Builder.Default
    private Integer streak = 0;

    /** 历史最高连胜 */
    @Column(name = "max_streak", nullable = false)
    @Builder.Default
    private Integer maxStreak = 0;

    /** 完赛次数（限时赛） */
    @Column(name = "timed_finished", nullable = false)
    @Builder.Default
    private Integer timedFinished = 0;

    @Column(name = "last_played_at")
    private LocalDateTime lastPlayedAt;
}
