package com.exam.backend.controller.competition;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.CompetitionDto;
import com.exam.backend.security.UserPrincipal;
import com.exam.backend.service.competition.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 战队 API (v4.0)：对齐文档 4.13 战队 7 端点，登录即可用（师生同权）。
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    public ApiResponse<List<CompetitionDto.TeamView>> list() {
        return ApiResponse.ok(teamService.list());
    }

    @PostMapping
    public ApiResponse<CompetitionDto.TeamView> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CompetitionDto.TeamRequest req) {
        return ApiResponse.ok(teamService.create(principal.getId(), req));
    }

    @GetMapping("/mine")
    public ApiResponse<CompetitionDto.TeamView> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(teamService.mine(principal.getId()));
    }

    @PostMapping("/{id}/join")
    public ApiResponse<CompetitionDto.TeamView> join(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(teamService.join(id, principal.getId()));
    }

    @PostMapping("/{id}/leave")
    public ApiResponse<Void> leave(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        teamService.leave(id, principal.getId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/transfer")
    public ApiResponse<CompetitionDto.TeamView> transfer(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody CompetitionDto.TransferRequest req) {
        return ApiResponse.ok(teamService.transfer(id, principal.getId(), req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> disband(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        teamService.disband(id, principal.getId());
        return ApiResponse.ok(null);
    }
}
