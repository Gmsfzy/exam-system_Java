package com.exam.backend.controller.competition;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.CompetitionDto;
import com.exam.backend.security.UserPrincipal;
import com.exam.backend.service.competition.CompetitionService;
import com.exam.backend.service.competition.LeaderboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 限时积分赛参赛 API (v4.0)：对齐文档 4.11，共 7 端点。
 * lobby / leaderboard 登录即可看；join/start/play/answer/finish 学生专属。
 */
@RestController
@RequestMapping("/api/competitions")
@RequiredArgsConstructor
public class CompetitionPlayController {

    private final CompetitionService competitionService;
    private final LeaderboardService leaderboardService;

    @GetMapping("/lobby")
    public ApiResponse<List<CompetitionDto.LobbyItem>> lobby(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(competitionService.lobby(principal.getId()));
    }

    @GetMapping("/{id}/leaderboard")
    public ApiResponse<CompetitionDto.LeaderboardResponse> leaderboard(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(leaderboardService.leaderboard(id, principal.getId()));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{id}/join")
    public ApiResponse<CompetitionDto.ParticipantView> join(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(competitionService.join(id, principal.getId()));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{id}/start")
    public ApiResponse<CompetitionDto.StartResponse> start(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(competitionService.start(id, principal.getId()));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/{id}/play")
    public ApiResponse<CompetitionDto.PlayStateResponse> play(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(competitionService.playState(id, principal.getId()));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{id}/answer")
    public ApiResponse<CompetitionDto.AnswerResponse> answer(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody CompetitionDto.AnswerRequest req) {
        return ApiResponse.ok(competitionService.answer(id, principal.getId(), req));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{id}/finish")
    public ApiResponse<CompetitionDto.FinishResponse> finish(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(competitionService.finish(id, principal.getId()));
    }
}
