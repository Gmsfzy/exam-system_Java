package com.exam.backend.domain.entity.competition;

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
 * 竞赛 (v4.0)：状态生命周期 draft → published → ongoing → ended，
 * 由 syncStatus() 按时间窗惰性同步（到开始转 ongoing、过结束转 ended 并结算）。
 * competitionType: timed / pk / quick_answer（当前仅 timed 实现，PK 以限时赛题源发起）
 */
@Entity
@Table(name = "competition")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Competition extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "competition_type", nullable = false, length = 20)
    @Builder.Default
    private String competitionType = "timed";

    /** draft / published / ongoing / ended */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "draft";

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 每人答题时长（分钟），从点击开始答题起算；也是速度分时间基准 */
    @Column(nullable = false)
    @Builder.Default
    private Integer duration = 15;

    /** 每人随机抽题数；0 或 ≥ 题池数时答全部 */
    @Column(name = "draw_count", nullable = false)
    @Builder.Default
    private Integer drawCount = 0;

    /** 计分规则 JSON：{base_ratio, speed_ratio}，解析归一化，默认 0.7/0.3 */
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "scoring_rule", columnDefinition = "TEXT")
    private Map<String, Object> scoringRule;

    /** 发布时按题目快照汇总 */
    @Column(name = "total_score", nullable = false)
    @Builder.Default
    private Double totalScore = 0.0;

    @Column(name = "allow_pk", nullable = false)
    @Builder.Default
    private Boolean allowPk = false;

    /** 限时赛积分统一结算 CAS 幂等标志 */
    @Column(name = "points_settled", nullable = false)
    @Builder.Default
    private Boolean pointsSettled = false;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;
}
