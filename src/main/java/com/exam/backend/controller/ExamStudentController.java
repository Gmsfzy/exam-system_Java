package com.exam.backend.controller;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.ExamDto;
import com.exam.backend.security.UserPrincipal;
import com.exam.backend.service.ExamStudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ExamStudentController {

    private final ExamStudentService examStudentService;

    @GetMapping("/students")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<ExamDto.StudentBriefResponse>> listAllStudents() {
        return ApiResponse.ok(examStudentService.listAllStudents());
    }

    @PostMapping("/exam/join/{code}")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<ExamDto.JoinResponse> joinByCode(@PathVariable String code,
                                                         @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examStudentService.joinByCode(code, principal.getId()));
    }
}
