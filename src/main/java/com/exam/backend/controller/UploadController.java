package com.exam.backend.controller;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.UploadDto;
import com.exam.backend.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<UploadDto.UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(uploadService.upload(file));
    }
}
