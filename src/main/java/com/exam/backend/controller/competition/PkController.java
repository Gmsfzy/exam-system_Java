package com.exam.backend.controller.competition;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.CompetitionDto;
import com.exam.backend.security.UserPrincipal;
import com.exam.backend.service.competition.PkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 1v1 PK 对战 API (v4.0，学生)：对齐文档 4.12，共 6 端点。
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class PkController {

    private final PkService pkService;

    @PostMapping("/competitions/{id}/pk")
    public ApiResponse<CompetitionDto.PkBattleView> create(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @RequestBody(required = false) CompetitionDto.PkCreateRequest req) {
        return ApiResponse.ok(pkService.create(id, principal.getId(), req));
    }

    @GetMapping("/pk/lobby")
    public ApiResponse<CompetitionDto.PkLobbyResponse> lobby(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(pkService.lobby(principal.getId()));
    }

    @PostMapping("/pk/{battleId}/accept")
    public ApiResponse<CompetitionDto.PkStateResponse> accept(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long battleId) {
        return ApiResponse.ok(pkService.accept(battleId, principal.getId()));
    }

    @GetMapping("/pk/{battleId}/state")
    public ApiResponse<CompetitionDto.PkStateResponse> state(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long battleId) {
        return ApiResponse.ok(pkService.state(battleId, principal.getId()));
    }

    @PostMapping("/pk/{battleId}/answer")
    public ApiResponse<CompetitionDto.PkAnswerResponse> answer(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long battleId,
            @Valid @RequestBody CompetitionDto.PkAnswerRequest req) {
        return ApiResponse.ok(pkService.answer(battleId, principal.getId(), req));
    }

    @PostMapping("/pk/{battleId}/finish")
    public ApiResponse<CompetitionDto.PkStateResponse> finish(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long battleId) {
        return ApiResponse.ok(pkService.finish(battleId, principal.getId()));
    }
}
