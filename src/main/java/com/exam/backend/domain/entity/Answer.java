package com.exam.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "answer")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Answer extends BaseEntity {

    /** 主观题 AI 判分状态机（v4.0 异步化）；客观题判分后置为 {@link #GRADED} */
    public static final String PENDING_AI = "pending_ai";  // 待 AI 判分
    public static final String GRADING = "grading";        // 已抢占，AI 判分中
    public static final String GRADED = "graded";          // AI/客观判分完成
    public static final String FAILED = "failed";          // AI 失败，转人工兜底

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "student_answer", columnDefinition = "TEXT")
    private String studentAnswer;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column
    private Double score;

    @Column(name = "manual_score")
    private Double manualScore;

    @Column(name = "manual_comment", length = 512)
    private String manualComment;

    @Column(name = "needs_manual_grade", nullable = false)
    @Builder.Default
    private Boolean needsManualGrade = false;

    @Column(name = "ai_score")
    private Double aiScore;

    @Column(name = "ai_analysis", columnDefinition = "TEXT")
    private String aiAnalysis;

    /** 主观题判分状态：pending_ai / grading / graded / failed；客观题为 graded，未作答主观题为 null */
    @Column(name = "grade_status", length = 16)
    private String gradeStatus;

    /**
     * 有效得分: 优先人工评分 > AI 评分 > 自动评分
     */
    public Double effectiveScore() {
        if (manualScore != null) return manualScore;
        if (aiScore != null) return aiScore;
        return score;
    }
}
