package com.exam.backend.service;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.Exam;
import com.exam.backend.domain.entity.ExamQuestion;
import com.exam.backend.domain.entity.Question;
import com.exam.backend.domain.enums.ExamStatusEnum;
import com.exam.backend.dto.ExamDto;
import com.exam.backend.repository.ExamQuestionRepository;
import com.exam.backend.repository.ExamRepository;
import com.exam.backend.repository.QuestionRepository;
import com.exam.backend.util.TimeZoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final QuestionRepository questionRepository;

    @Transactional(readOnly = true)
    public List<ExamDto.ExamResponse> listForTeacher(Long creatorId) {
        return examRepository.findByCreatorId(creatorId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ExamDto.ExamResponse get(Long id, Long currentUserId, String currentRole) {
        Exam e = requireExam(id);
        boolean isTeacher = "teacher".equalsIgnoreCase(currentRole);
        boolean isCreator = e.getCreatorId().equals(currentUserId);
        if (!isTeacher && !isCreator) {
            return new ExamDto.ExamResponse(e.getId(), e.getTitle(), e.getDescription(),
                    e.getStartTime(), e.getEndTime(), e.getDuration(),
                    e.getStatus(), e.getCreatorId(),
                    null, null,
                    e.getCreatedAt());
        }
        return toResponse(e);
    }

    @Transactional
    public ExamDto.ExamResponse create(ExamDto.ExamRequest req, Long creatorId) {
        Exam e = Exam.builder()
                .title(req.title())
                .description(req.description())
                .startTime(TimeZoneUtil.fromInput(req.startTime()))
                .endTime(TimeZoneUtil.fromInput(req.endTime()))
                .duration(req.duration())
                .status(ExamStatusEnum.draft)
                .creatorId(creatorId)
                .build();
        examRepository.save(e);
        return toResponse(e);
    }

    @Transactional
    public ExamDto.ExamResponse update(Long id, ExamDto.ExamRequest req, Long currentUserId) {
        Exam e = requireOwned(id, currentUserId);
        if (e.getStatus() == ExamStatusEnum.ended) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已结束的考试不可修改");
        }
        if (req.title() != null) e.setTitle(req.title());
        if (req.description() != null) e.setDescription(req.description());
        if (req.startTime() != null) e.setStartTime(TimeZoneUtil.fromInput(req.startTime()));
        if (req.endTime() != null) e.setEndTime(TimeZoneUtil.fromInput(req.endTime()));
        if (req.duration() != null) e.setDuration(req.duration());
        return toResponse(e);
    }

    @Transactional
    public ExamDto.ExamResponse publish(Long id, Long currentUserId) {
        Exam e = requireOwned(id, currentUserId);
        if (e.getStatus() != ExamStatusEnum.draft) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅草稿态考试可发布");
        }
        if (examQuestionRepository.countByExamId(id) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "考试无题目,无法发布");
        }
        if (e.getStartTime() == null || e.getEndTime() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "考试起止时间不能为空");
        }
        e.setStatus(ExamStatusEnum.published);
        return toResponse(e);
    }

    @Transactional
    public ExamDto.ExamResponse end(Long id, Long currentUserId) {
        Exam e = requireOwned(id, currentUserId);
        if (e.getStatus() != ExamStatusEnum.published) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅已发布考试可结束");
        }
        e.setStatus(ExamStatusEnum.ended);
        return toResponse(e);
    }

    @Transactional
    public void delete(Long id, Long currentUserId) {
        Exam e = requireOwned(id, currentUserId);
        examQuestionRepository.findByExamId(id).forEach(examQuestionRepository::delete);
        examRepository.delete(e);
    }

    @Transactional(readOnly = true)
    public List<ExamDto.ExamQuestionView> listExamQuestions(Long examId, Long currentUserId, String currentRole) {
        Exam e = requireExam(examId);
        // 仅考试创建者教师可访问含答案的题目列表
        if (!"teacher".equalsIgnoreCase(currentRole) || !e.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅考试创建教师可访问考试题目");
        }
        List<ExamQuestion> eqs = examQuestionRepository.findByExamIdOrderByOrderAsc(examId);
        List<Long> qids = eqs.stream().map(ExamQuestion::getQuestionId).toList();
        if (qids.isEmpty()) return List.of();
        List<Question> qs = questionRepository.findAllById(qids);
        var qMap = qs.stream().collect(Collectors.toMap(Question::getId, q -> q));
        return eqs.stream()
                .map(eq -> {
                    Question q = qMap.get(eq.getQuestionId());
                    if (q == null) return null;
                    return new ExamDto.ExamQuestionView(
                            q.getId(), q.getContent(), q.getOptions(),
                            q.getType(), q.getDifficulty(),
                            eq.getScore(), eq.getOrder());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public ExamDto.AddQuestionsRequest addQuestions(Long examId, ExamDto.AddQuestionsRequest req, Long currentUserId) {
        Exam e = requireOwned(examId, currentUserId);
        if (e.getStatus() == ExamStatusEnum.ended) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已结束考试不可修改题目");
        }
        List<ExamQuestion> existing = examQuestionRepository.findByExamId(examId);
        int order = existing.stream().mapToInt(eq -> eq.getOrder() == null ? 0 : eq.getOrder()).max().orElse(0);
        for (Long qid : req.questionIds()) {
            if (!questionRepository.existsById(qid)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在: " + qid);
            }
            if (existing.stream().anyMatch(eq -> eq.getQuestionId().equals(qid))) continue;
            ExamQuestion eq = ExamQuestion.builder()
                    .examId(examId)
                    .questionId(qid)
                    .score(10)
                    .order(++order)
                    .build();
            examQuestionRepository.save(eq);
        }
        return req;
    }

    @Transactional
    public ExamDto.RemoveQuestionResponse removeQuestion(Long examId, Long questionId, Long currentUserId) {
        requireOwned(examId, currentUserId);
        examQuestionRepository.deleteByExamIdAndQuestionId(examId, questionId);
        return new ExamDto.RemoveQuestionResponse(examId, questionId, true);
    }

    @Transactional
    public ExamDto.InvitationResponse generateInvitation(Long examId, Long currentUserId) {
        Exam e = requireOwned(examId, currentUserId);
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String url = "/exam/join/" + code;
        e.setInvitationCode(code);
        e.setInvitationUrl(url);
        String qrDataUrl = "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=" + url;
        return new ExamDto.InvitationResponse(code, url, qrDataUrl);
    }

    @Transactional
    public ExamDto.SmartCompositionResponse smartComposition(Long examId,
                                                               ExamDto.SmartCompositionRequest req,
                                                               Long currentUserId) {
        requireOwned(examId, currentUserId);
        // 抽题: 按条件筛选
        List<Question> pool = questionRepository.filterForUser(
                req.majorId(), null, null, req.type(), req.difficulty(), currentUserId);

        // source_filter 进一步过滤
        if (req.sourceFilter() != null) {
            String sf = req.sourceFilter();
            pool = pool.stream().filter(q -> {
                if ("mine".equalsIgnoreCase(sf)) return currentUserId.equals(q.getCreatorId());
                if ("public".equalsIgnoreCase(sf)) return Boolean.TRUE.equals(q.getIsPublic());
                return true;
            }).toList();
        }
        if (pool.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "无可用的题目满足条件");
        }
        int need = req.count() == null ? 5 : Math.max(1, Math.min(req.count(), pool.size()));
        // 随机抽题
        List<Question> picked = pickRandom(pool, need);
        // 去重 - 不重复添加
        List<ExamQuestion> existing = examQuestionRepository.findByExamId(examId);
        var existIds = existing.stream().map(ExamQuestion::getQuestionId).collect(Collectors.toSet());
        int order = existing.stream().mapToInt(eq -> eq.getOrder() == null ? 0 : eq.getOrder()).max().orElse(0);
        List<Long> addedIds = new ArrayList<>();
        for (Question q : picked) {
            if (existIds.contains(q.getId())) continue;
            ExamQuestion eq = ExamQuestion.builder()
                    .examId(examId)
                    .questionId(q.getId())
                    .score(10)
                    .order(++order)
                    .build();
            examQuestionRepository.save(eq);
            addedIds.add(q.getId());
        }
        return new ExamDto.SmartCompositionResponse(examId, addedIds.size(), addedIds);
    }

    private List<Question> pickRandom(List<Question> pool, int n) {
        List<Question> copy = new ArrayList<>(pool);
        SecureRandom rnd = new SecureRandom();
        List<Question> picked = new ArrayList<>();
        while (n > 0 && !copy.isEmpty()) {
            int idx = rnd.nextInt(copy.size());
            picked.add(copy.remove(idx));
            n--;
        }
        return picked;
    }

    public Exam requireOwned(Long id, Long currentUserId) {
        Exam e = examRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "考试不存在"));
        if (!e.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅考试创建者可操作");
        }
        return e;
    }

    public Exam requireExam(Long id) {
        return examRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "考试不存在"));
    }

    public ExamDto.ExamResponse toResponse(Exam e) {
        return new ExamDto.ExamResponse(e.getId(), e.getTitle(), e.getDescription(),
                e.getStartTime(), e.getEndTime(), e.getDuration(),
                e.getStatus(), e.getCreatorId(),
                e.getInvitationCode(), e.getInvitationUrl(),
                e.getCreatedAt());
    }
}