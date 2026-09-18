package com.exam.backend.domain.entity.competition;

import com.exam.backend.domain.converter.JsonLongListConverter;
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
 * 限时赛参与者 (v4.0)：status joined → playing → finished。
 * assignedCqIds 在 start 时随机抽题固化，此后不可变（null/空 = 全量模式答全部）。
 */
@Entity
@Table(name = "competition_participant",
        uniqueConstraints = @UniqueConstraint(name = "uk_comp_participant", columnNames = {"competition_id", "user_id"}),
        indexes = @Index(name = "idx_comp_part_user", columnList = "user_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompetitionParticipant extends BaseEntity {

    @Column(name = "competition_id", nullable = false)
    private Long competitionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** joined / playing / finished */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "joined";

    @Convert(converter = JsonLongListConverter.class)
    @Column(name = "assigned_cq_ids", columnDefinition = "TEXT")
    private List<Long> assignedCqIds;

    @Column(nullable = false)
    @Builder.Default
    private Double score = 0.0;

    /** 已用时间（秒），完成后回写 */
    @Column(name = "used_time", nullable = false)
    @Builder.Default
    private Integer usedTime = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
