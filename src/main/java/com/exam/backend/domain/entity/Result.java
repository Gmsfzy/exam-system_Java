package com.exam.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "result")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Result extends BaseEntity {

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "score")
    private Double score;

    @Column(name = "total_score")
    private Double totalScore;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "ai_analysis", columnDefinition = "TEXT")
    private String aiAnalysis;

    /** 是否仍有主观题处于 AI 异步评分中（v4.0）；为 true 时前端展示“AI 评分中” */
    @Column(name = "grading")
    @Builder.Default
    private Boolean grading = false;
}
