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

/**
 * 赛季归档快照 (v4.0)：赛季结束时逐用户落库，供历史查询。
 * rank 为 SQL 保留字，列名转义。
 */
@Entity
@Table(name = "season_archive",
        indexes = @Index(name = "idx_season_archive_season", columnList = "season"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SeasonArchive extends BaseEntity {

    @Column(nullable = false, length = 10)
    private String season;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "\"rank\"", nullable = false)
    private Integer rank;

    @Column(nullable = false)
    @Builder.Default
    private Integer points = 0;

    /** 归档时的段位标识 */
    @Column(length = 32)
    private String tier;

    @Column(name = "pk_win", nullable = false)
    @Builder.Default
    private Integer pkWin = 0;

    @Column(name = "pk_total", nullable = false)
    @Builder.Default
    private Integer pkTotal = 0;

    @Column(name = "timed_finished", nullable = false)
    @Builder.Default
    private Integer timedFinished = 0;
}
