package com.exam.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class LogDto {
    public record LogRequest(
            @NotBlank String level,
            @NotBlank String message,
            String source,
            String component,
            java.util.Map<String, Object> meta) {}
}
