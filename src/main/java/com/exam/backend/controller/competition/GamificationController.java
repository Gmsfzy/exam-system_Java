package com.exam.backend.controller.competition;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.CompetitionDto;
import com.exam.backend.security.UserPrincipal;
import com.exam.backend.service.competition.GamificationQueryService;
import com.exam.backend.service.competition.SeasonService;
import com.exam.backend.service.competition.StudyProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 竞技化 API (v4.0)：段位 / 赛季榜 / 勋章墙 / 学情画像，对齐文档 4.13，共 7 端点。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GamificationController {

    private final GamificationQueryService gamificationQueryService;
    private final SeasonService seasonService;
    private final StudyProfileService studyProfileService;

    @GetMapping("/rank/me")
    public ApiResponse<CompetitionDto.RankMeView> rankMe(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(gamificationQueryService.rankMe(principal.getId()));
    }

    @GetMapping("/rank/seasons")
    public ApiResponse<List<String>> seasons() {
        return ApiResponse.ok(seasonService.listSeasons());
    }

    @GetMapping("/rank/season/{season}")
    public ApiResponse<CompetitionDto.SeasonRankView> seasonRank(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable String season) {
        return ApiResponse.ok(seasonService.seasonRank(season, principal.getId()));
    }

    @GetMapping("/rank/archives")
    public ApiResponse<List<CompetitionDto.ArchiveView>> archives(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(seasonService.myArchives(principal.getId()));
    }

    @GetMapping("/badges/me")
    public ApiResponse<List<CompetitionDto.BadgeView>> badges(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(gamificationQueryService.badges(principal.getId()));
    }

    @GetMapping("/profile/study")
    public ApiResponse<CompetitionDto.StudyProfileView> myProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(studyProfileService.build(principal.getId()));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/profile/study/{studentId}")
    public ApiResponse<CompetitionDto.StudyProfileView> studentProfile(@PathVariable Long studentId) {
        return ApiResponse.ok(studyProfileService.build(studentId));
    }
}
