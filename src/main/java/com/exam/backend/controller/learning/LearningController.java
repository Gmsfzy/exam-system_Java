package com.exam.backend.controller.learning;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.LearningDto;
import com.exam.backend.security.UserPrincipal;
import com.exam.backend.service.learning.LearningAskService;
import com.exam.backend.service.learning.PracticeService;
import com.exam.backend.service.learning.StudyPlanService;
import com.exam.backend.service.learning.StudyReportService;
import com.exam.backend.service.learning.WrongAnswerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 自主学习 API (v4.0)：对齐文档 4.17，共 18 端点（含 AI 答疑）。
 * 仅校验登录，不区分教师/学生角色（与文档一致）。
 */
@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
public class LearningController {

    private final WrongAnswerService wrongAnswerService;
    private final PracticeService practiceService;
    private final StudyPlanService studyPlanService;
    private final StudyReportService studyReportService;
    private final LearningAskService learningAskService;

    // ==================== 错题本 ====================

    @GetMapping("/wrong-records")
    public ApiResponse<List<LearningDto.WrongRecordView>> listWrongRecords(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Boolean mastered) {
        return ApiResponse.ok(wrongAnswerService.list(principal.getId(), sourceType, mastered));
    }

    @PostMapping("/wrong-records/{id}/master")
    public ApiResponse<LearningDto.WrongRecordView> masterWrongRecord(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(wrongAnswerService.markMastered(principal.getId(), id));
    }

    @DeleteMapping("/wrong-records/{id}")
    public ApiResponse<Void> deleteWrongRecord(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        wrongAnswerService.delete(principal.getId(), id);
        return ApiResponse.ok(null);
    }

    // ==================== 自由刷题 ====================

    @PostMapping("/practice/start")
    public ApiResponse<LearningDto.PracticeStartResponse> practiceStart(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LearningDto.PracticeStartRequest req) {
        return ApiResponse.ok(practiceService.start(principal.getId(), req));
    }

    @GetMapping("/practice/history")
    public ApiResponse<List<LearningDto.PracticeHistoryItem>> practiceHistory(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(practiceService.history(principal.getId()));
    }

    @GetMapping("/practice/{id}")
    public ApiResponse<LearningDto.PracticeDetailView> practiceDetail(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(practiceService.detail(principal.getId(), id));
    }

    @PostMapping("/practice/{id}/answer")
    public ApiResponse<LearningDto.PracticeAnswerResponse> practiceAnswer(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody LearningDto.PracticeAnswerRequest req) {
        return ApiResponse.ok(practiceService.answer(principal.getId(), id, req));
    }

    @PostMapping("/practice/{id}/submit")
    public ApiResponse<LearningDto.PracticeSubmitResponse> practiceSubmit(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(practiceService.complete(principal.getId(), id));
    }

    @DeleteMapping("/practice/{id}")
    public ApiResponse<Void> practiceDelete(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        practiceService.delete(principal.getId(), id);
        return ApiResponse.ok(null);
    }

    // ==================== 学习计划 ====================

    @GetMapping("/plans")
    public ApiResponse<List<LearningDto.PlanView>> listPlans(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(studyPlanService.list(principal.getId(), status));
    }

    @PostMapping("/plans")
    public ApiResponse<LearningDto.PlanView> createPlan(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LearningDto.PlanRequest req) {
        return ApiResponse.ok(studyPlanService.create(principal.getId(), req));
    }

    @GetMapping("/plans/{id}")
    public ApiResponse<LearningDto.PlanView> getPlan(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(studyPlanService.get(principal.getId(), id));
    }

    @PutMapping("/plans/{id}")
    public ApiResponse<LearningDto.PlanView> updatePlan(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody LearningDto.PlanRequest req) {
        return ApiResponse.ok(studyPlanService.update(principal.getId(), id, req));
    }

    @PostMapping("/plans/{id}/pause")
    public ApiResponse<LearningDto.PlanView> togglePausePlan(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(studyPlanService.togglePause(principal.getId(), id));
    }

    @DeleteMapping("/plans/{id}")
    public ApiResponse<Void> deletePlan(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        studyPlanService.delete(principal.getId(), id);
        return ApiResponse.ok(null);
    }

    // ==================== 学习报告 ====================

    @GetMapping("/report/overview")
    public ApiResponse<LearningDto.ReportOverview> reportOverview(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(studyReportService.overview(principal.getId()));
    }

    @GetMapping("/report/daily")
    public ApiResponse<List<LearningDto.DailyReportItem>> reportDaily(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Integer days) {
        return ApiResponse.ok(studyReportService.daily(principal.getId(), days));
    }

    // ==================== AI 答疑 ====================

    @PostMapping("/ask")
    public ApiResponse<LearningDto.AskResponse> ask(
            @Valid @RequestBody LearningDto.AskRequest req) {
        return ApiResponse.ok(learningAskService.ask(req));
    }
}
