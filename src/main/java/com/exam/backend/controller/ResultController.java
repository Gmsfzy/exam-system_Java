package com.exam.backend.controller;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.ResultDto;
import com.exam.backend.security.UserPrincipal;
import com.exam.backend.service.ResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/results")
@RequiredArgsConstructor
public class ResultController {

    private final ResultService resultService;

    @GetMapping("/me")
    public ApiResponse<List<ResultDto.ResultSummary>> myResults(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(resultService.listMyResults(principal.getId()));
    }

    @GetMapping("/exam/{examId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<ResultDto.ResultSummary>> examResults(@PathVariable Long examId,
                                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(resultService.listExamResults(examId, principal.getId(), principal.getRole()));
    }

    @GetMapping("/{resultId}")
    public ApiResponse<ResultDto.ResultDetail> detail(@PathVariable Long resultId,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(resultService.detail(resultId, principal.getId(), principal.getRole()));
    }

    @GetMapping("/grading/{examId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<ResultDto.GradeStudentView>> gradingList(@PathVariable Long examId,
                                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(resultService.gradingList(examId, principal.getId()));
    }

    @GetMapping("/grading/{examId}/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<ResultDto.GradeableAnswerView>> gradingDetail(@PathVariable Long examId,
                                                                           @PathVariable Long studentId,
                                                                           @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(resultService.gradingDetail(examId, studentId, principal.getId()));
    }

    @PostMapping("/grading/{examId}/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<ResultDto.ManualGradeResponse> manualGrade(@PathVariable Long examId,
                                                                   @PathVariable Long studentId,
                                                                   @Valid @RequestBody ResultDto.ManualGradeRequest req,
                                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(resultService.manualGrade(examId, studentId, req, principal.getId()));
    }

    @GetMapping("/analysis/{examId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<ResultDto.AnalysisResponse> analyze(@PathVariable Long examId,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(resultService.analyze(examId, principal.getId()));
    }
}
