package com.exam.backend.controller.bounty;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.BountyDto;
import com.exam.backend.security.UserPrincipal;
import com.exam.backend.service.bounty.BountyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 征集悬赏 API (v4.0)，对齐文档 4.14，共 9 端点。师生同权：登录即可发布/投稿，仅发布者审核。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BountyController {

    private final BountyService bountyService;

    @GetMapping("/bounties")
    public ApiResponse<List<BountyDto.BountyListItem>> plaza(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(bountyService.list(type, status, principal.getId()));
    }

    @PostMapping("/bounties")
    public ApiResponse<BountyDto.BountyDetailView> publish(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BountyDto.BountyRequest request) {
        return ApiResponse.ok(bountyService.publish(request, principal.getId()));
    }

    @GetMapping("/bounties/mine")
    public ApiResponse<List<BountyDto.BountyListItem>> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(bountyService.mine(principal.getId()));
    }

    @GetMapping("/bounties/my-submissions")
    public ApiResponse<List<BountyDto.MySubmissionItem>> mySubmissions(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(bountyService.mySubmissions(principal.getId()));
    }

    @GetMapping("/bounties/{id}")
    public ApiResponse<BountyDto.BountyDetailView> detail(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(bountyService.detail(id, principal.getId(), principal.isTeacher()));
    }

    @GetMapping("/bounties/{id}/submissions")
    public ApiResponse<List<BountyDto.SubmissionView>> submissions(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.ok(bountyService.submissions(id, principal.getId()));
    }

    @PostMapping("/bounties/{id}/submissions")
    public ApiResponse<BountyDto.SubmissionView> submit(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody BountyDto.SubmissionRequest request) {
        return ApiResponse.ok(bountyService.submit(id, request, principal.getId()));
    }

    @PostMapping("/submissions/{sid}/accept")
    public ApiResponse<BountyDto.AcceptResponse> accept(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long sid) {
        BountyDto.AcceptOutcome outcome = bountyService.accept(sid, principal.getId());
        bountyService.settleReward(outcome);
        return ApiResponse.ok(new BountyDto.AcceptResponse(
                "采纳成功", outcome.acceptedQuestionId(), outcome.rewardPoints()));
    }

    @PostMapping("/submissions/{sid}/reject")
    public ApiResponse<Void> reject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sid,
            @RequestBody(required = false) BountyDto.RejectRequest request) {
        bountyService.reject(sid, request == null ? null : request.reviewComment(), principal.getId());
        return ApiResponse.ok();
    }
}
