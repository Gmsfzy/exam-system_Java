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
 * 勋章发放记录 (v4.0)：唯一约束 (user_id, badge_code, season) 保证幂等发放。
 * badgeCode 见 GamificationService 定义（首战告捷/三连胜/五连王者/身经百战/一战封神/季度巅峰/开疆辟土 + 悬赏新星）。
 */
@Entity
@Table(name = "user_badge",
        uniqueConstraints = @UniqueConstraint(name = "uk_badge_user_code_season",
                columnNames = {"user_id", "badge_code", "season"}),
        indexes = @Index(name = "idx_badge_user", columnList = "user_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserBadge extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "badge_code", nullable = false, length = 40)
    private String badgeCode;

    @Column(nullable = false, length = 10)
    private String season;

    /** 关联业务 id（如触发勋章的 battle/competition id），可空 */
    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "granted_at")
    private LocalDateTime grantedAt;
}
