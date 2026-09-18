package com.exam.backend.controller;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.LogDto;
import com.exam.backend.service.LogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> receive(@Valid @RequestBody LogDto.LogRequest req) {
        logService.receive(req, "Frontend");
        return ApiResponse.ok();
    }

    @PostMapping("/vue")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> receiveVue(@Valid @RequestBody LogDto.LogRequest req) {
        logService.receive(req, "Vue3");
        return ApiResponse.ok();
    }
}