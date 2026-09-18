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

import java.time.LocalDateTime;

/**
 * 自由刷题会话 (v4.0 自学)：不计分不计时，纯练习。
 * status: in_progress / completed（硬编码字符串）
 */
@Entity
@Table(name = "practice_session")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PracticeSession extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 128)
    private String title;

    @Column(name = "major_id")
    private Long majorId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "in_progress";

    @Column(name = "questions_count", nullable = false)
    @Builder.Default
    private Integer questionsCount = 0;

    @Column(name = "correct_count", nullable = false)
    @Builder.Default
    private Integer correctCount = 0;

    @Column(name = "total_time_sec", nullable = false)
    @Builder.Default
    private Integer totalTimeSec = 0;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;
}
