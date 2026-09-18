package com.exam.backend.controller;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.AiDto;
import com.exam.backend.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/ai/generate")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<AiDto.AiGenerateResponse> generate(@Valid @RequestBody AiDto.AiGenerateRequest req) {
        List<AiDto.AiGeneratedQuestion> qs = aiService.generate(req);
        return ApiResponse.ok(new AiDto.AiGenerateResponse(qs));
    }

    @GetMapping("/question-types")
    public ApiResponse<List<AiDto.QuestionTypeView>> questionTypes() {
        return ApiResponse.ok(aiService.questionTypes());
    }

    @GetMapping("/difficulties")
    public ApiResponse<List<AiDto.DifficultyView>> difficulties() {
        return ApiResponse.ok(aiService.difficulties());
    }
}
