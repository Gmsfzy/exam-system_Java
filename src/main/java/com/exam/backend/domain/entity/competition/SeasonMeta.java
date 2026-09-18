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
 * 赛季元信息 (v4.0)：season 'YYYY-MM' 唯一，归档 CAS 抢锁靠 archived 标志。
 */
@Entity
@Table(name = "season_meta",
        uniqueConstraints = @UniqueConstraint(name = "uk_season_meta", columnNames = {"season"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SeasonMeta extends BaseEntity {

    @Column(nullable = false, length = 10)
    private String season;

    @Column(nullable = false)
    @Builder.Default
    private Boolean archived = false;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
}
