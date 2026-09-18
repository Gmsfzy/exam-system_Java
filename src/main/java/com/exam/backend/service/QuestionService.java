package com.exam.backend.service;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.Question;
import com.exam.backend.domain.enums.DifficultyEnum;
import com.exam.backend.domain.enums.QuestionTypeEnum;
import com.exam.backend.dto.QuestionDto;
import com.exam.backend.repository.ExamQuestionRepository;
import com.exam.backend.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final ExamQuestionRepository examQuestionRepository;

    @Transactional(readOnly = true)
    public Page<QuestionDto.QuestionResponse> list(String scope, Long majorId, Long courseId,
                                                    Long chapterId, QuestionTypeEnum type,
                                                    DifficultyEnum difficulty,
                                                    Boolean isStudent,
                                                    Long currentUserId,
                                                    int page, int size) {
        String effectiveScope = scope == null ? "all" : scope;
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200)));
        Page<Question> p = questionRepository.findScoped(effectiveScope, currentUserId,
                majorId, courseId, chapterId, type, difficulty, pageable);
        return p.map(q -> toResponse(q, isStudent));
    }

    @Transactional(readOnly = true)
    public QuestionDto.QuestionResponse get(Long id, Boolean isStudent) {
        Question q = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
        return toResponse(q, isStudent);
    }

    @Transactional
    public QuestionDto.QuestionResponse create(QuestionDto.QuestionRequest req, Long creatorId) {
        Question q = Question.builder()
                .content(req.content())
                .options(req.options())
                .answer(req.answer())
                .analysis(req.analysis())
                .knowledge(req.knowledge())
                .type(req.type())
                .difficulty(req.difficulty())
                .source(req.source())
                .creatorId(creatorId)
                .isPublic(req.isPublic() != null && req.isPublic())
                .majorId(req.majorId())
                .courseId(req.courseId())
                .chapterId(req.chapterId())
                .build();
        questionRepository.save(q);
        return toResponse(q, false);
    }

    @Transactional
    public QuestionDto.QuestionResponse update(Long id, QuestionDto.QuestionRequest req, Long currentUserId) {
        Question q = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
        if (q.getCreatorId() == null || !q.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅题目创建者可编辑");
        }
        if (req.content() != null) q.setContent(req.content());
        if (req.options() != null) q.setOptions(req.options());
        if (req.answer() != null) q.setAnswer(req.answer());
        if (req.analysis() != null) q.setAnalysis(req.analysis());
        if (req.knowledge() != null) q.setKnowledge(req.knowledge());
        if (req.type() != null) q.setType(req.type());
        if (req.difficulty() != null) q.setDifficulty(req.difficulty());
        if (req.source() != null) q.setSource(req.source());
        if (req.isPublic() != null) q.setIsPublic(req.isPublic());
        if (req.majorId() != null) q.setMajorId(req.majorId());
        if (req.courseId() != null) q.setCourseId(req.courseId());
        if (req.chapterId() != null) q.setChapterId(req.chapterId());
        return toResponse(q, false);
    }

    @Transactional
    public void delete(Long id, Long currentUserId) {
        Question q = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
        if (q.getCreatorId() == null || !q.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅题目创建者可删除");
        }
        if (examQuestionRepository.existsByQuestionId(id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "题目已被考试引用,无法删除");
        }
        questionRepository.deleteById(id);
    }

    @Transactional
    public QuestionDto.TogglePublicResponse togglePublic(Long id, Long currentUserId) {
        Question q = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
        if (q.getCreatorId() == null || !q.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅题目创建者可切换公开状态");
        }
        q.setIsPublic(!q.getIsPublic());
        return new QuestionDto.TogglePublicResponse(q.getId(), q.getIsPublic());
    }

    private QuestionDto.QuestionResponse toResponse(Question q, Boolean isStudent) {
        boolean student = Boolean.TRUE.equals(isStudent);
        return new QuestionDto.QuestionResponse(
                q.getId(), q.getContent(), q.getOptions(),
                student ? "" : q.getAnswer(),
                student ? "" : q.getAnalysis(),
                q.getKnowledge(), q.getType(), q.getDifficulty(),
                q.getSource(), q.getCreatorId(), q.getIsPublic(),
                q.getMajorId(), q.getCourseId(), q.getChapterId(),
                q.getCreatedAt());
    }
}
