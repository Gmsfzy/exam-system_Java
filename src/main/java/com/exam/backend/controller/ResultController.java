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

    // ==== M6 申诉 / 聚类批注 / 同题批量给分 ====

    @PostMapping("/{resultId}/review")
    public ApiResponse<ResultDto.ReviewResponse> requestReview(@PathVariable Long resultId,
                                                               @RequestBody ResultDto.ReviewRequest req,
                                                               @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(resultService.requestReview(resultId, req, principal.getId()));
    }

    @PostMapping("/{resultId}/review_handle")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<ResultDto.ReviewResponse> handleReview(@PathVariable Long resultId,
                                                              @Valid @RequestBody ResultDto.ReviewHandleRequest req,
                                                              @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(resultService.handleReview(resultId, req, principal.getId()));
    }

    @GetMapping("/grading/{examId}/cluster")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<ResultDto.ClusterItem>> gradingCluster(@PathVariable Long examId,
                                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(resultService.gradingCluster(examId, principal.getId()));
    }

    @PostMapping("/grading/{examId}/question_batch")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<ResultDto.BatchGradeResponse> batchGradeQuestion(@PathVariable Long examId,
                                                                        @Valid @RequestBody ResultDto.BatchGradeRequest req,
                                                                        @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(resultService.batchGradeQuestion(examId, req, principal.getId()));
    }
}
