package com.exam.backend.controller;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.MajorDto;
import com.exam.backend.service.MajorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MajorController {

    private final MajorService majorService;

    @GetMapping("/departments")
    public ApiResponse<List<MajorDto.DepartmentResponse>> listDepartments() {
        return ApiResponse.ok(majorService.listDepartments());
    }

    @GetMapping("/majors")
    public ApiResponse<List<MajorDto.MajorResponse>> listMajors(@RequestParam(required = false) Long departmentId) {
        return ApiResponse.ok(majorService.listMajors(departmentId));
    }

    @GetMapping("/majors/{id}")
    public ApiResponse<MajorDto.MajorResponse> getMajor(@PathVariable Long id) {
        return ApiResponse.ok(majorService.getMajor(id));
    }

    @PostMapping("/majors")
    public ApiResponse<MajorDto.MajorResponse> createMajor(@RequestBody MajorDto.MajorRequest req) {
        return ApiResponse.ok(majorService.createMajor(req));
    }

    @PutMapping("/majors/{id}")
    public ApiResponse<MajorDto.MajorResponse> updateMajor(@PathVariable Long id,
                                                            @RequestBody MajorDto.MajorRequest req) {
        return ApiResponse.ok(majorService.updateMajor(id, req));
    }

    @DeleteMapping("/majors/{id}")
    public ApiResponse<Void> deleteMajor(@PathVariable Long id) {
        majorService.deleteMajor(id);
        return ApiResponse.ok();
    }
}
