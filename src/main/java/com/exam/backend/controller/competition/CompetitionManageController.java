package com.exam.backend.controller.competition;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.CompetitionDto;
import com.exam.backend.security.UserPrincipal;
import com.exam.backend.service.competition.CompetitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 竞赛管理 API (v4.0，教师)：对齐文档 4.10，共 10 端点。
 * 教师仅能操作自己创建的竞赛（服务层 requireOwn 兜底）。
 */
@RestController
@RequestMapping("/api/competitions")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class CompetitionManageController {

    private final CompetitionService competitionService;

    @GetMapping
    public ApiResponse<List<CompetitionDto.CompetitionView>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(competitionService.listForTeacher(principal.getId()));
    }

    @PostMapping
    public ApiResponse<CompetitionDto.CompetitionView> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CompetitionDto.CompetitionRequest req) {
        return ApiResponse.ok(competitionService.create(principal.getId(), req));
    }

    @GetMapping("/{id}")
    public ApiResponse<CompetitionDto.CompetitionDetailVO> detail(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(competitionService.detail(id, principal.getId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<CompetitionDto.CompetitionView> update(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody CompetitionDto.CompetitionRequest req) {
        return ApiResponse.ok(competitionService.update(id, principal.getId(), req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        competitionService.delete(id, principal.getId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/questions")
    public ApiResponse<Map<String, Object>> addQuestions(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody CompetitionDto.AddQuestionsRequest req) {
        int added = competitionService.addQuestions(id, principal.getId(), req.questionIds());
        return ApiResponse.ok(Map.of("added", added));
    }

    @DeleteMapping("/{id}/questions/{cqId}")
    public ApiResponse<Void> removeQuestion(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id, @PathVariable Long cqId) {
        competitionService.removeQuestion(id, cqId, principal.getId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<CompetitionDto.CompetitionView> publish(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(competitionService.publish(id, principal.getId()));
    }

    @PostMapping("/{id}/end")
    public ApiResponse<CompetitionDto.CompetitionView> end(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(competitionService.end(id, principal.getId()));
    }

    @GetMapping("/{id}/participants")
    public ApiResponse<List<CompetitionDto.ParticipantView>> participants(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(competitionService.participants(id, principal.getId()));
    }
}
