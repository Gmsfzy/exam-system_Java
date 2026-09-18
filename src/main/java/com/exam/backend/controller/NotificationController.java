package com.exam.backend.controller;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.NotificationDto;
import com.exam.backend.security.UserPrincipal;
import com.exam.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<NotificationDto.NotificationResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(notificationService.list(principal.getId()));
    }

    @GetMapping("/unread")
    public ApiResponse<NotificationDto.UnreadCountResponse> unread(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(notificationService.unreadCount(principal.getId()));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markRead(id, principal.getId());
        return ApiResponse.ok();
    }

    @PutMapping("/read_all")
    public ApiResponse<Integer> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(notificationService.markAllRead(principal.getId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.delete(id, principal.getId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/clear_all")
    public ApiResponse<Integer> clearAll(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(notificationService.clearAll(principal.getId()));
    }
}
