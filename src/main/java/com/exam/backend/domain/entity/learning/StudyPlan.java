package com.exam.backend.domain.entity.learning;

import com.exam.backend.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 学习计划 (v4.0 自学)：暂停/恢复/进度追踪。
 * status: active / paused / completed（硬编码字符串）
 * completedCount 随刷题/练习自动更新。
 */
@Entity
@Table(name = "study_plan")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudyPlan extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_count", nullable = false)
    @Builder.Default
    private Integer targetCount = 10;

    @Column(name = "completed_count", nullable = false)
    @Builder.Default
    private Integer completedCount = 0;

    @Column(name = "major_id")
    private Long majorId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "chapter_id")
    private Long chapterId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "active";
}
