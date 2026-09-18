package com.exam.backend.service;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.Exam;
import com.exam.backend.domain.entity.ExamStudent;
import com.exam.backend.domain.enums.ExamStatusEnum;
import com.exam.backend.domain.enums.RoleEnum;
import com.exam.backend.dto.ExamDto;
import com.exam.backend.repository.ExamRepository;
import com.exam.backend.repository.ExamStudentRepository;
import com.exam.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExamStudentService {

    private final ExamRepository examRepository;
    private final ExamStudentRepository examStudentRepository;
    private final UserRepository userRepository;
    private final ExamService examService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<ExamDto.StudentBriefResponse> listAllStudents() {
        return userRepository.findByRole(RoleEnum.student).stream()
                .map(s -> new ExamDto.StudentBriefResponse(s.getId(), s.getUsername(), s.getEmail()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExamDto.StudentBriefResponse> listExamStudents(Long examId, Long currentUserId) {
        examService.requireOwned(examId, currentUserId);
        List<ExamStudent> refs = examStudentRepository.findByExamId(examId);
        List<Long> sids = refs.stream().map(ExamStudent::getStudentId).toList();
        if (sids.isEmpty()) return List.of();
        return userRepository.findAllById(sids).stream()
                .map(s -> new ExamDto.StudentBriefResponse(s.getId(), s.getUsername(), s.getEmail()))
                .toList();
    }

    @Transactional
    public ExamDto.InviteResponse inviteStudents(Long examId, ExamDto.InviteRequest req, Long currentUserId) {
        Exam e = examService.requireOwned(examId, currentUserId);
        List<Long> existingIds = new ArrayList<>();
        for (Long sid : req.studentIds()) {
            if (!userRepository.existsById(sid)) continue;
            if (examStudentRepository.existsByExamIdAndStudentId(examId, sid)) {
                existingIds.add(sid);
                continue;
            }
            ExamStudent es = ExamStudent.builder()
                    .examId(examId)
                    .studentId(sid)
                    .invitedAt(LocalDateTime.now())
                    .build();
            examStudentRepository.save(es);
            notificationService.notifyStudentInvited(sid, examId, e.getTitle());
        }
        return new ExamDto.InviteResponse(examId, req.studentIds().size() - existingIds.size(), existingIds);
    }

    @Transactional
    public ExamDto.RemoveStudentResponse removeStudent(Long examId, Long studentId, Long currentUserId) {
        examService.requireOwned(examId, currentUserId);
        examStudentRepository.deleteByExamIdAndStudentId(examId, studentId);
        return new ExamDto.RemoveStudentResponse(examId, studentId, true);
    }

    @Transactional
    public ExamDto.JoinResponse joinByCode(String rawCode, Long studentId) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "邀请码不能为空");
        }
        String code = rawCode.trim().toUpperCase();
        Exam e = examRepository.findByInvitationCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "邀请码无效"));
        if (e.getStatus() != ExamStatusEnum.published) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "考试当前不可加入");
        }
        // 幂等加入
        if (!examStudentRepository.existsByExamIdAndStudentId(e.getId(), studentId)) {
            ExamStudent es = ExamStudent.builder()
                    .examId(e.getId())
                    .studentId(studentId)
                    .invitedAt(LocalDateTime.now())
                    .build();
            examStudentRepository.save(es);
        }
        return new ExamDto.JoinResponse(e.getId(), e.getTitle(), studentId);
    }

    @Transactional(readOnly = true)
    public List<ExamDto.ExamResponse> listForStudent(Long studentId) {
        List<ExamStudent> refs = examStudentRepository.findByStudentId(studentId);
        if (refs.isEmpty()) return List.of();
        List<Long> eids = refs.stream().map(ExamStudent::getExamId).toList();
        List<Exam> exams = examRepository.findAllById(eids);
        return exams.stream().map(examService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Exam> findPublishedByCode(String code) {
        return examRepository.findByInvitationCode(code);
    }
}
