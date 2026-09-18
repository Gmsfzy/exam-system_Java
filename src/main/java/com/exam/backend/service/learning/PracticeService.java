package com.exam.backend.service.learning;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.Question;
import com.exam.backend.domain.entity.learning.PracticeAnswer;
import com.exam.backend.domain.entity.learning.PracticeSession;
import com.exam.backend.domain.entity.learning.StudyPlan;
import com.exam.backend.domain.enums.QuestionTypeEnum;
import com.exam.backend.dto.LearningDto;
import com.exam.backend.repository.QuestionRepository;
import com.exam.backend.repository.learning.PracticeAnswerRepository;
import com.exam.backend.repository.learning.PracticeSessionRepository;
import com.exam.backend.repository.learning.StudyPlanRepository;
import com.exam.backend.util.AnswerComparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 自由刷题子模块 (v4.0 自学)：不计分不计时，抽题仅客观题，单题即时判分反馈。
 * start 时预存 PracticeAnswer 占位行，刷新可恢复；删除会话由本层级联清理作答。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PracticeService {

    private static final int DEFAULT_COUNT = 10;
    private static final int MAX_COUNT = 50;
    /** 练习只抽客观题（AnswerComparator 对主观题一律返回 false） */
    private static final List<QuestionTypeEnum> OBJECTIVE_TYPES = List.of(
            QuestionTypeEnum.single_choice, QuestionTypeEnum.multiple_choice,
            QuestionTypeEnum.fill_blank, QuestionTypeEnum.true_false);

    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeAnswerRepository practiceAnswerRepository;
    private final StudyPlanRepository studyPlanRepository;
    private final QuestionRepository questionRepository;
    private final AnswerComparator answerComparator;
    private final WrongAnswerService wrongAnswerService;
    private final StudyLogService studyLogService;

    @Transactional
    public LearningDto.PracticeStartResponse start(Long userId, LearningDto.PracticeStartRequest req) {
        List<Question> pool = questionRepository.findPracticePool(
                req.majorId(), req.courseId(), req.chapterId(), OBJECTIVE_TYPES, userId);
        if (pool.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "题库中暂无符合条件的客观题，无法开始练习");
        }
        int count = req.count() == null || req.count() <= 0 ? DEFAULT_COUNT : Math.min(req.count(), MAX_COUNT);
        List<Question> sampled = new ArrayList<>(pool);
        Collections.shuffle(sampled);
        sampled = sampled.subList(0, Math.min(count, sampled.size()));

        PracticeSession session = practiceSessionRepository.save(PracticeSession.builder()
                .userId(userId)
                .title(req.title() == null || req.title().isBlank() ? "自由练习" : req.title().trim())
                .majorId(req.majorId())
                .courseId(req.courseId())
                .status("in_progress")
                .questionsCount(sampled.size())
                .correctCount(0)
                .totalTimeSec(0)
                .startTime(LocalDateTime.now())
                .build());
        // 预存占位作答行，支持刷新恢复
        List<PracticeAnswer> placeholders = sampled.stream()
                .map(q -> PracticeAnswer.builder()
                        .sessionId(session.getId())
                        .questionId(q.getId())
                        .build())
                .toList();
        practiceAnswerRepository.saveAll(placeholders);

        List<LearningDto.PracticeQuestionView> views = sampled.stream()
                .map(q -> new LearningDto.PracticeQuestionView(
                        q.getId(), q.getContent(), q.getOptions(),
                        q.getType(), q.getDifficulty(), q.getKnowledge(), null, null))
                .toList();
        return new LearningDto.PracticeStartResponse(
                session.getId(), session.getStatus(), session.getQuestionsCount(), views);
    }

    @Transactional(readOnly = true)
    public LearningDto.PracticeDetailView detail(Long userId, Long sessionId) {
        PracticeSession session = requireOwned(userId, sessionId);
        List<PracticeAnswer> answers = practiceAnswerRepository.findBySessionIdOrderByIdAsc(sessionId);
        List<Long> qids = answers.stream().map(PracticeAnswer::getQuestionId).toList();
        Map<Long, Question> qmap = qids.isEmpty() ? Map.of()
                : questionRepository.findAllById(qids).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        List<LearningDto.PracticeQuestionView> views = answers.stream().map(pa -> {
            Question q = qmap.get(pa.getQuestionId());
            if (q == null) return null;
            return new LearningDto.PracticeQuestionView(
                    q.getId(), q.getContent(), q.getOptions(),
                    q.getType(), q.getDifficulty(), q.getKnowledge(),
                    pa.getStudentAnswer(), pa.getIsCorrect());
        }).filter(Objects::nonNull).toList();

        return new LearningDto.PracticeDetailView(
                session.getId(), session.getTitle(), session.getStatus(),
                session.getQuestionsCount(), session.getCorrectCount(), session.getTotalTimeSec(),
                session.getStartTime(), session.getEndTime(), views);
    }

    @Transactional
    public LearningDto.PracticeAnswerResponse answer(Long userId, Long sessionId,
                                                     LearningDto.PracticeAnswerRequest req) {
        PracticeSession session = requireOwned(userId, sessionId);
        if (!"in_progress".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该练习会话已完成，不可再作答");
        }
        PracticeAnswer pa = practiceAnswerRepository
                .findBySessionIdAndQuestionId(sessionId, req.questionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "该题目不属于本练习会话"));
        Question q = questionRepository.findById(req.questionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));

        String studentAnswer = req.studentAnswer() == null ? "" : req.studentAnswer().trim();
        pa.setStudentAnswer(studentAnswer);
        pa.setTimeSpentSec(req.timeSpentSec());
        if (studentAnswer.isEmpty()) {
            // 清空作答：回到未答状态
            pa.setIsCorrect(null);
            pa.setAnsweredAt(null);
            practiceAnswerRepository.save(pa);
            return new LearningDto.PracticeAnswerResponse(q.getId(), null, q.getAnswer(), q.getAnalysis());
        }
        boolean correct = answerComparator.isCorrect(q.getType(), q.getAnswer(), studentAnswer);
        pa.setIsCorrect(correct);
        pa.setAnsweredAt(LocalDateTime.now());
        practiceAnswerRepository.save(pa);
        if (!correct) {
            wrongAnswerService.recordWrong(userId, q, studentAnswer, "practice", sessionId);
        }
        return new LearningDto.PracticeAnswerResponse(q.getId(), correct, q.getAnswer(), q.getAnalysis());
    }

    @Transactional
    public LearningDto.PracticeSubmitResponse complete(Long userId, Long sessionId) {
        PracticeSession session = requireOwned(userId, sessionId);
        if ("completed".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该练习会话已提交");
        }
        List<PracticeAnswer> answers = practiceAnswerRepository.findBySessionIdOrderByIdAsc(sessionId);
        int answered = 0;
        int correct = 0;
        int timeSpent = answers.stream()
                .map(PracticeAnswer::getTimeSpentSec)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        for (PracticeAnswer pa : answers) {
            if (pa.getStudentAnswer() != null && !pa.getStudentAnswer().isBlank()) answered++;
            if (Boolean.TRUE.equals(pa.getIsCorrect())) correct++;
        }
        LocalDateTime now = LocalDateTime.now();
        session.setStatus("completed");
        session.setCorrectCount(correct);
        session.setTotalTimeSec(timeSpent);
        session.setEndTime(now);
        practiceSessionRepository.save(session);

        // 学习日志 + 计划进度（同事务，练习完成即记录）
        studyLogService.writePracticeLog(userId, sessionId, answers.size(),
                correct, session.getStartTime(), now);
        advancePlans(userId, session, answered);

        double accuracy = answers.size() > 0 ? (double) correct / answers.size() : 0.0;
        return new LearningDto.PracticeSubmitResponse(
                sessionId, answers.size(), answered, correct, accuracy, timeSpent);
    }

    @Transactional
    public void delete(Long userId, Long sessionId) {
        PracticeSession session = requireOwned(userId, sessionId);
        practiceAnswerRepository.deleteBySessionId(sessionId);
        practiceSessionRepository.delete(session);
    }

    @Transactional(readOnly = true)
    public List<LearningDto.PracticeHistoryItem> history(Long userId) {
        return practiceSessionRepository.findByUserIdOrderByStartTimeDesc(userId).stream()
                .map(s -> new LearningDto.PracticeHistoryItem(
                        s.getId(), s.getTitle(), s.getStatus(),
                        s.getQuestionsCount(), s.getCorrectCount(), s.getTotalTimeSec(),
                        s.getStartTime(), s.getEndTime()))
                .toList();
    }

    /** 完成答题数推进匹配的活跃计划，达标自动 completed */
    private void advancePlans(Long userId, PracticeSession session, int answeredCount) {
        if (answeredCount <= 0) return;
        List<StudyPlan> plans = studyPlanRepository.findActiveMatching(userId, session.getMajorId(), session.getCourseId());
        for (StudyPlan plan : plans) {
            int next = (plan.getCompletedCount() == null ? 0 : plan.getCompletedCount()) + answeredCount;
            plan.setCompletedCount(Math.min(next, plan.getTargetCount()));
            if (plan.getCompletedCount() >= plan.getTargetCount()) {
                plan.setStatus("completed");
            }
            studyPlanRepository.save(plan);
        }
    }

    private PracticeSession requireOwned(Long userId, Long sessionId) {
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "练习会话不存在"));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该练习会话");
        }
        return session;
    }
}
