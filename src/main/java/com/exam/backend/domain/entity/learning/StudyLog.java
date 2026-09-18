package com.exam.backend.domain.entity.learning;

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

import java.time.LocalDateTime;

/**
 * 学习日志 (v4.0 自学)：考试/竞赛/练习结束后聚合写入，供学习报告查询。
 * logType: exam / competition / practice（硬编码字符串）
 */
@Entity
@Table(name = "study_log",
        indexes = @Index(name = "idx_study_log_user_time", columnList = "user_id,studied_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudyLog extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "log_type", nullable = false, length = 20)
    private String logType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "total_questions", nullable = false)
    @Builder.Default
    private Integer totalQuestions = 0;

    @Column(name = "correct_count", nullable = false)
    @Builder.Default
    private Integer correctCount = 0;

    /** 得分率 0~1（考试为得分/满分，练习为正确率） */
    @Column(name = "score_ratio", nullable = false)
    @Builder.Default
    private Double scoreRatio = 0.0;

    @Column(name = "time_spent_sec", nullable = false)
    @Builder.Default
    private Integer timeSpentSec = 0;

    @Column(name = "studied_at", nullable = false)
    private LocalDateTime studiedAt;
}
