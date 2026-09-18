package com.exam.backend.controller;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.MajorDto;
import com.exam.backend.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping("/courses")
    public ApiResponse<List<MajorDto.CourseResponse>> listCourses(@RequestParam(required = false) Long majorId) {
        return ApiResponse.ok(courseService.listCourses(majorId));
    }

    @PostMapping("/courses")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<MajorDto.CourseResponse> createCourse(@RequestBody MajorDto.CourseRequest req) {
        return ApiResponse.ok(courseService.createCourse(req));
    }

    @PutMapping("/courses/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<MajorDto.CourseResponse> updateCourse(@PathVariable Long id,
                                                               @RequestBody MajorDto.CourseRequest req) {
        return ApiResponse.ok(courseService.updateCourse(id, req));
    }

    @DeleteMapping("/courses/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<Void> deleteCourse(@PathVariable Long id) {
        courseService.deleteCourse(id);
        return ApiResponse.ok();
    }
}
