package com.exam.backend.controller;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.domain.enums.DifficultyEnum;
import com.exam.backend.domain.enums.QuestionTypeEnum;
import com.exam.backend.dto.QuestionDto;
import com.exam.backend.security.UserPrincipal;
import com.exam.backend.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @GetMapping
    public ApiResponse<Page<QuestionDto.QuestionResponse>> list(
            @RequestParam(required = false, defaultValue = "all") String scope,
            @RequestParam(required = false) Long majorId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long chapterId,
            @RequestParam(required = false) QuestionTypeEnum type,
            @RequestParam(required = false) DifficultyEnum difficulty,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isStudent = principal != null && principal.isStudent();
        return ApiResponse.ok(questionService.list(scope, majorId, courseId, chapterId,
                type, difficulty, isStudent, principal.getId(), page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionDto.QuestionResponse> get(@PathVariable Long id,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        boolean isStudent = principal != null && principal.isStudent();
        return ApiResponse.ok(questionService.get(id, isStudent));
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<QuestionDto.QuestionResponse> create(@Valid @RequestBody QuestionDto.QuestionRequest req,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(questionService.create(req, principal.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<QuestionDto.QuestionResponse> update(@PathVariable Long id,
                                                             @Valid @RequestBody QuestionDto.QuestionRequest req,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(questionService.update(id, req, principal.getId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        questionService.delete(id, principal.getId());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/toggle_public")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<QuestionDto.TogglePublicResponse> togglePublic(@PathVariable Long id,
                                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(questionService.togglePublic(id, principal.getId()));
    }
}
