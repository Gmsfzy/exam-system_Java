package com.exam.backend.dto;

import com.exam.backend.domain.enums.DifficultyEnum;
import com.exam.backend.domain.enums.QuestionTypeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class QuestionDto {

    public record QuestionRequest(
            @NotBlank(message = "题目内容不能为空") String content,
            List<String> options,
            String answer,
            String analysis,
            String knowledge,
            @NotNull(message = "题型不能为空") QuestionTypeEnum type,
            @NotNull(message = "难度不能为空") DifficultyEnum difficulty,
            String source,
            Long majorId,
            Long courseId,
            Long chapterId,
            Boolean isPublic,
            // M6 材料题：同组题目共享阅读材料
            String material,
            String materialGroup) {}

    /** 教师组卷视图：含 answer/analysis */
    public record QuestionResponse(
            Long id, String content, List<String> options, String answer, String analysis,
            String knowledge, QuestionTypeEnum type, DifficultyEnum difficulty,
            String source, Long creatorId, Boolean isPublic,
            Long majorId, Long courseId, Long chapterId, LocalDateTime createdAt,
            String material, String materialGroup) {}

    /** 学生答题视图：去除 answer/analysis */
    public record QuestionStudentView(
            Long id, String content, List<String> options,
            QuestionTypeEnum type, DifficultyEnum difficulty,
            Long majorId, Long courseId, Long chapterId) {}

    public record TogglePublicResponse(Long id, Boolean isPublic) {}
}
