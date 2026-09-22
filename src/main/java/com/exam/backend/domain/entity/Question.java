package com.exam.backend.domain.entity;

import com.exam.backend.domain.converter.JsonStringListConverter;
import com.exam.backend.domain.enums.DifficultyEnum;
import com.exam.backend.domain.enums.QuestionTypeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "question")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Question extends BaseEntity {

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Convert(converter = JsonStringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> options;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(columnDefinition = "TEXT")
    private String analysis;

    @Column(length = 256)
    private String knowledge;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private QuestionTypeEnum type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DifficultyEnum difficulty;

    @Column(length = 64)
    private String source;

    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = false;

    @Column(name = "major_id")
    private Long majorId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "chapter_id")
    private Long chapterId;

    // ==== M6 材料题（同组题目共享阅读材料） ====

    /** 共享阅读材料正文 */
    @Column(columnDefinition = "TEXT")
    private String material;

    /** 材料组标识，同组题目渲染时共用一个材料卡 */
    @Column(name = "material_group", length = 64)
    private String materialGroup;
}
