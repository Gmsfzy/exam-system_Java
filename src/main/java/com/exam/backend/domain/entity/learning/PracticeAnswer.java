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
 * 练习会话内单题作答 (v4.0 自学)
 * start 时预存占位行（studentAnswer 为空），供刷新恢复；删除会话时由服务层级联清理。
 */
@Entity
@Table(name = "practice_answer",
        indexes = @Index(name = "idx_practice_answer_session", columnList = "session_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PracticeAnswer extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "student_answer", columnDefinition = "TEXT")
    private String studentAnswer;

    /** null = 未作答 */
    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "time_spent_sec")
    private Integer timeSpentSec;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;
}
