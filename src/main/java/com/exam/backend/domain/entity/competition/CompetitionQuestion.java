package com.exam.backend.domain.entity.competition;

import com.exam.backend.domain.converter.JsonStringListConverter;
import com.exam.backend.domain.entity.BaseEntity;
import com.exam.backend.domain.enums.QuestionTypeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 竞赛题目快照 (v4.0)：添加题目时从题库复制 content/options/answer/score，
 * 发布后与题库解耦；仅客观题。
 */
@Entity
@Table(name = "competition_question",
        indexes = @Index(name = "idx_cq_competition", columnList = "competition_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompetitionQuestion extends BaseEntity {

    @Column(name = "competition_id", nullable = false)
    private Long competitionId;

    /** 来源题库题 id（题库题被删不影响快照） */
    @Column(name = "question_id")
    private Long questionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "q_type", nullable = false, length = 32)
    private QuestionTypeEnum qType;

    @Column(name = "q_content", nullable = false, columnDefinition = "TEXT")
    private String qContent;

    @Convert(converter = JsonStringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> options;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(nullable = false)
    @Builder.Default
    private Double score = 10.0;

    @Column(name = "\"order\"")
    @Builder.Default
    private Integer order = 0;
}
