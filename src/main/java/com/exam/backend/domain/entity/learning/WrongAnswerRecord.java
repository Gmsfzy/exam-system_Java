package com.exam.backend.domain.entity.learning;

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
 * 错题本记录 (v4.0 自学)
 * 唯一约束 (user_id, question_id)：同一题重复答错时幂等累加 wrongCount
 * sourceType: exam / competition / practice（硬编码字符串，与文档一致）
 */
@Entity
@Table(name = "wrong_answer_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_wrong_user_question", columnNames = {"user_id", "question_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WrongAnswerRecord extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "wrong_answer", columnDefinition = "TEXT")
    private String wrongAnswer;

    @Column(name = "correct_answer", columnDefinition = "TEXT")
    private String correctAnswer;

    @Column(name = "is_mastered", nullable = false)
    @Builder.Default
    private Boolean isMastered = false;

    @Column(name = "wrong_count", nullable = false)
    @Builder.Default
    private Integer wrongCount = 1;

    @Column(name = "last_wrong_at")
    private LocalDateTime lastWrongAt;

    @Column(name = "mastered_at")
    private LocalDateTime masteredAt;
}
