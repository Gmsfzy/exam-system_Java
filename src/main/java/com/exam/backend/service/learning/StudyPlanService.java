package com.exam.backend.service.learning;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.learning.StudyPlan;
import com.exam.backend.dto.LearningDto;
import com.exam.backend.repository.learning.StudyPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 学习计划子模块 (v4.0 自学)：CRUD + 暂停/恢复。
 * status 硬编码：active / paused / completed；completedCount 由练习提交自动推进（见 PracticeService）。
 */
@Service
@RequiredArgsConstructor
public class StudyPlanService {

    private final StudyPlanRepository studyPlanRepository;

    @Transactional(readOnly = true)
    public List<LearningDto.PlanView> list(Long userId, String status) {
        List<StudyPlan> plans = (status == null || status.isBlank())
                ? studyPlanRepository.findByUserIdOrderByIdDesc(userId)
                : studyPlanRepository.findByUserIdAndStatus(userId, status);
        return plans.stream().map(this::toView).toList();
    }

    @Transactional
    public LearningDto.PlanView create(Long userId, LearningDto.PlanRequest req) {
        StudyPlan plan = StudyPlan.builder()
                .userId(userId)
                .title(req.title().trim())
                .description(req.description())
                .targetCount(req.targetCount() == null || req.targetCount() <= 0 ? 10 : req.targetCount())
                .completedCount(0)
                .majorId(req.majorId())
                .courseId(req.courseId())
                .chapterId(req.chapterId())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .status("active")
                .build();
        return toView(studyPlanRepository.save(plan));
    }

    @Transactional(readOnly = true)
    public LearningDto.PlanView get(Long userId, Long id) {
        return toView(requireOwned(userId, id));
    }

    @Transactional
    public LearningDto.PlanView update(Long userId, Long id, LearningDto.PlanRequest req) {
        StudyPlan plan = requireOwned(userId, id);
        plan.setTitle(req.title().trim());
        plan.setDescription(req.description());
        if (req.targetCount() != null && req.targetCount() > 0) {
            plan.setTargetCount(req.targetCount());
            // 目标下调后可能不再达标；上调后又可能重新达标
            if (plan.getCompletedCount() >= plan.getTargetCount()) {
                plan.setStatus("completed");
            } else if ("completed".equals(plan.getStatus())) {
                plan.setStatus("active");
            }
        }
        plan.setMajorId(req.majorId());
        plan.setCourseId(req.courseId());
        plan.setChapterId(req.chapterId());
        plan.setStartDate(req.startDate());
        plan.setEndDate(req.endDate());
        return toView(studyPlanRepository.save(plan));
    }

    /** active ↔ paused 互切；completed 状态不允许切换 */
    @Transactional
    public LearningDto.PlanView togglePause(Long userId, Long id) {
        StudyPlan plan = requireOwned(userId, id);
        switch (plan.getStatus()) {
            case "active" -> plan.setStatus("paused");
            case "paused" -> plan.setStatus("active");
            default -> throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已完成的计划不能暂停或恢复");
        }
        return toView(studyPlanRepository.save(plan));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        studyPlanRepository.delete(requireOwned(userId, id));
    }

    private StudyPlan requireOwned(Long userId, Long id) {
        StudyPlan plan = studyPlanRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "学习计划不存在"));
        if (!plan.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该学习计划");
        }
        return plan;
    }

    private LearningDto.PlanView toView(StudyPlan p) {
        return new LearningDto.PlanView(
                p.getId(), p.getTitle(), p.getDescription(),
                p.getTargetCount(), p.getCompletedCount(),
                p.getMajorId(), p.getCourseId(), p.getChapterId(),
                p.getStartDate(), p.getEndDate(),
                p.getStatus(), p.getCreatedAt());
    }
}
