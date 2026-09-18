package com.exam.backend.controller;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.ExamExecutionDto;
import com.exam.backend.security.UserPrincipal;
import com.exam.backend.service.ExamExecutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exam/{id}")
@RequiredArgsConstructor
public class ExamExecutionController {

    private final ExamExecutionService examExecutionService;

    @PostMapping("/start")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<ExamExecutionDto.StartExamResponse> start(@PathVariable Long id,
                                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examExecutionService.start(id, principal.getId()));
    }

    @GetMapping("/take")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<ExamExecutionDto.TakeExamResponse> take(@PathVariable Long id,
                                                                @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examExecutionService.take(id, principal.getId()));
    }

    @PostMapping("/save_answer")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<ExamExecutionDto.SaveAnswerResponse> saveAnswer(@PathVariable Long id,
                                                                        @Valid @RequestBody ExamExecutionDto.SaveAnswerRequest req,
                                                                        @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examExecutionService.saveAnswer(id, principal.getId(), req));
    }

    @PostMapping("/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<ExamExecutionDto.SubmitResponse> submit(@PathVariable Long id,
                                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examExecutionService.submit(id, principal.getId()));
    }

    @PostMapping("/report_switch")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<ExamExecutionDto.ReportSwitchResponse> reportSwitch(@PathVariable Long id,
                                                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examExecutionService.reportSwitch(id, principal.getId()));
    }

    @GetMapping("/report")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<ExamExecutionDto.ExamReportResponse> report(@PathVariable Long id,
                                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examExecutionService.report(id, principal.getId()));
    }
}
