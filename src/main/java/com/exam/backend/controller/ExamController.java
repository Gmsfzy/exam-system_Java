package com.exam.backend.controller;

import com.exam.backend.common.ApiResponse;
import com.exam.backend.dto.ExamDto;
import com.exam.backend.security.UserPrincipal;
import com.exam.backend.service.ExamService;
import com.exam.backend.service.ExamStudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final ExamStudentService examStudentService;

    @GetMapping
    public ApiResponse<List<ExamDto.ExamResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal.isStudent()) {
            return ApiResponse.ok(examStudentService.listForStudent(principal.getId()));
        }
        return ApiResponse.ok(examService.listForTeacher(principal.getId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExamDto.ExamResponse> get(@PathVariable Long id,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examService.get(id, principal.getId(), principal.getRole()));
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<ExamDto.ExamResponse> create(@Valid @RequestBody ExamDto.ExamRequest req,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examService.create(req, principal.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<ExamDto.ExamResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody ExamDto.ExamRequest req,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examService.update(id, req, principal.getId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        examService.delete(id, principal.getId());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<ExamDto.ExamResponse> publish(@PathVariable Long id,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examService.publish(id, principal.getId()));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<ExamDto.ExamResponse> end(@PathVariable Long id,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examService.end(id, principal.getId()));
    }

    @GetMapping("/{id}/questions")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<ExamDto.ExamQuestionView>> listQuestions(@PathVariable Long id,
                                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examService.listExamQuestions(id, principal.getId(), principal.getRole()));
    }

    @PostMapping("/{id}/add_questions")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<ExamDto.AddQuestionsRequest> addQuestions(@PathVariable Long id,
                                                                   @RequestBody ExamDto.AddQuestionsRequest req,
                                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examService.addQuestions(id, req, principal.getId()));
    }

    @PostMapping("/{id}/remove_question/{questionId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<ExamDto.RemoveQuestionResponse> removeQuestion(@PathVariable Long id,
                                                                       @PathVariable Long questionId,
                                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examService.removeQuestion(id, questionId, principal.getId()));
    }

    @PostMapping("/{id}/generate_invitation")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<ExamDto.InvitationResponse> generateInvitation(@PathVariable Long id,
                                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examService.generateInvitation(id, principal.getId()));
    }

    @PostMapping("/{id}/smart_composition")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<ExamDto.SmartCompositionResponse> smartComposition(@PathVariable Long id,
                                                                           @RequestBody ExamDto.SmartCompositionRequest req,
                                                                           @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examService.smartComposition(id, req, principal.getId()));
    }

    @GetMapping("/{id}/students")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<ExamDto.StudentBriefResponse>> listStudents(@PathVariable Long id,
                                                                         @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examStudentService.listExamStudents(id, principal.getId()));
    }

    @PostMapping("/{id}/invite")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<ExamDto.InviteResponse> invite(@PathVariable Long id,
                                                        @RequestBody ExamDto.InviteRequest req,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examStudentService.inviteStudents(id, req, principal.getId()));
    }

    @PostMapping("/{id}/remove_student/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<ExamDto.RemoveStudentResponse> removeStudent(@PathVariable Long id,
                                                                      @PathVariable Long studentId,
                                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(examStudentService.removeStudent(id, studentId, principal.getId()));
    }
}
