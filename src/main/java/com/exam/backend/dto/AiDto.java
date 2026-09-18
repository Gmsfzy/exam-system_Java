package com.exam.backend.dto;

import com.exam.backend.domain.enums.DifficultyEnum;
import com.exam.backend.domain.enums.QuestionTypeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AiDto {

    public record AiGenerateRequest(
            @NotBlank String major,
            @NotNull QuestionTypeEnum type,
            @NotNull DifficultyEnum difficulty,
            String hint,
            Integer count) {}

    public record AiGeneratedQuestion(
            String content, java.util.List<String> options,
            String answer, String analysis, String knowledge,
            QuestionTypeEnum type, DifficultyEnum difficulty) {}

    public record AiGenerateResponse(java.util.List<AiGeneratedQuestion> questions) {}

    public record QuestionTypeView(String value, String label, Boolean isSubjective) {}

    public record DifficultyView(String value, String label) {}

    public record AiGradeRequest(Long answerId, String context) {}

    public record AiGradeResponse(Double score, String analysis) {}
}
