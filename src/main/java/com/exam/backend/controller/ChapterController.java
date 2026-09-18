package com.exam.backend.controller;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.MajorDto;
import com.exam.backend.service.ChapterService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chapters")
@RequiredArgsConstructor
public class ChapterController {

    private final ChapterService chapterService;

    @GetMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<MajorDto.ChapterResponse>> list(@RequestParam Long courseId) {
        return ApiResponse.ok(chapterService.listChapters(courseId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<MajorDto.ChapterResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(chapterService.getChapter(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<MajorDto.ChapterResponse> create(@RequestBody MajorDto.ChapterRequest req) {
        return ApiResponse.ok(chapterService.createChapter(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<MajorDto.ChapterResponse> update(@PathVariable Long id,
                                                          @RequestBody MajorDto.ChapterRequest req) {
        return ApiResponse.ok(chapterService.updateChapter(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        chapterService.deleteChapter(id);
        return ApiResponse.ok();
    }
}
