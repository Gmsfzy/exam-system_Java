package com.exam.backend.service;

import com.exam.backend.domain.entity.Answer;
import com.exam.backend.domain.entity.Exam;
import com.exam.backend.domain.entity.ExamQuestion;
import com.exam.backend.domain.entity.ExamSession;
import com.exam.backend.domain.entity.Question;
import com.exam.backend.domain.entity.Result;
import com.exam.backend.config.AsyncConfig;
import com.exam.backend.dto.AiDto;
import com.exam.backend.repository.AnswerRepository;
import com.exam.backend.repository.ExamQuestionRepository;
import com.exam.backend.repository.ExamRepository;
import com.exam.backend.repository.ExamSessionRepository;
import com.exam.backend.repository.QuestionRepository;
import com.exam.backend.repository.ResultRepository;
import com.exam.backend.service.competition.RealtimePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 主观题 AI 异步判分服务 (v4.0)。
 * <p>核心约束：<b>数据库事务绝不横跨 AI 远程调用</b>。流程为
 * 短事务 CAS 抢占（pending_ai→grading）→ 事务外逐题调用 AI → 逐题短事务回写（graded/failed）
 * → 短事务重算成绩。由交卷事务 AFTER_COMMIT 触发，运行于专用有界线程池，
 * 与 {@link SubjectiveGradingSweeper} 补偿扫描共用 {@link #gradeSession} 入口。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectiveGradingService {

    /** “判分中”卡死判定阈值（分钟）：超过则补偿扫描可重新抢占 */
    public static final int GRADING_STALE_MINUTES = 3;

    private final AnswerRepository answerRepository;
    private final ExamSessionRepository examSessionRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final ResultRepository resultRepository;
    private final AiService aiService;
    private final NotificationService notificationService;
    private final RealtimePushService pushService;

    /** 交卷事务提交后异步消费，DB 事务已释放，此处不再持有事务 */
    @Async(AsyncConfig.AI_GRADING_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSubjectiveGradingRequested(SubjectiveGradingRequested event) {
        try {
            gradeSession(event.sessionId());
        } catch (Exception e) {
            log.error("async subjective grading failed for session {}: {}", event.sessionId(), e.getMessage(), e);
        }
    }

    /** 线程池异步派发入口（供补偿扫描器复用，避免占用调度线程） */
    @Async(AsyncConfig.AI_GRADING_EXECUTOR)
    public void gradeSessionAsync(Long sessionId) {
        try {
            gradeSession(sessionId);
        } catch (Exception e) {
            log.error("async subjective grading failed for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 判分一个会话内所有待判主观题。可重入、并发安全（CAS 抢占保证同一行仅一方处理）。
     * 本方法<b>不带 @Transactional</b>：所有 DB 写通过仓储短事务完成，AI 调用在事务之外。
     */
    public void gradeSession(Long sessionId) {
        ExamSession session = examSessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            log.warn("gradeSession: session {} not found", sessionId);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusMinutes(GRADING_STALE_MINUTES);

        // 1) 短事务 CAS 抢占：pending_ai / 卡死 grading → grading
        int claimed = answerRepository.claimForGrading(sessionId, staleBefore, now);
        if (claimed == 0) {
            return; // 无待判题或已被其他工作者抢占
        }

        // 2) 读取抢占到的题目并快照所需数据（只读，事务外使用）
        List<Answer> toGrade = answerRepository.findBySessionIdAndGradeStatus(sessionId, Answer.GRADING);
        if (toGrade.isEmpty()) return;

        Long examId = session.getExamId();
        List<ExamQuestion> eqs = examQuestionRepository.findByExamIdOrderByOrderAsc(examId);
        Map<Long, Double> maxByQuestion = new LinkedHashMap<>();
        for (ExamQuestion eq : eqs) {
            maxByQuestion.put(eq.getQuestionId(), eq.getScore() == null ? 0.0 : eq.getScore());
        }
        Map<Long, Question> qmap = questionRepository
                .findAllById(toGrade.stream().map(Answer::getQuestionId).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(Question::getId, Function.identity()));

        // 3) 事务外逐题 AI 判分，逐题短事务回写（每题独立提交，任何时刻都不长期持锁）
        for (Answer a : toGrade) {
            Question q = qmap.get(a.getQuestionId());
            double maxScore = maxByQuestion.getOrDefault(a.getQuestionId(), 0.0);
            try {
                if (q == null) throw new IllegalStateException("题目不存在: " + a.getQuestionId());
                AiDto.AiGradeResponse grade = aiService.gradeSubjective(q, a, maxScore);
                a.setAiScore(grade.score());
                a.setAiAnalysis(grade.analysis());
                a.setNeedsManualGrade(false);
                a.setGradeStatus(Answer.GRADED);
            } catch (Exception e) {
                log.warn("AI grade failed qid={} session={}: {} → 转人工", a.getQuestionId(), sessionId, e.getMessage());
                a.setGradeStatus(Answer.FAILED);
                a.setNeedsManualGrade(true);
            }
            answerRepository.save(a);
        }

        // 4) 短事务重算成绩 + 完成通知/推送
        finalizeGradedResult(examId, session.getStudentId(), sessionId);
    }

    /** 重算 Result 得分与判分标志；若无待判题则发“成绩公布”通知并推送 result_ready */
    private void finalizeGradedResult(Long examId, Long studentId, Long sessionId) {
        Result result = resultRepository.findByExamIdAndStudentId(examId, studentId).orElse(null);
        if (result == null) return;

        List<Answer> answers = answerRepository.findBySessionId(sessionId);
        double total = 0.0;
        for (Answer a : answers) {
            Double eff = a.effectiveScore();
            if (eff != null) total += eff;
        }
        long pending = answerRepository.countUngraded(sessionId);

        result.setScore(com.exam.backend.service.competition.ScoringService.round2(total));
        result.setGrading(pending > 0);
        resultRepository.save(result);

        if (pending == 0) {
            Exam exam = examRepository.findById(examId).orElse(null);
            String title = exam == null ? ("#" + examId) : exam.getTitle();
            try {
                notificationService.notifyResultPublished(studentId, examId, title, result.getScore());
            } catch (Exception e) {
                log.warn("notify result published failed exam={} student={}: {}", examId, studentId, e.getMessage());
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("examId", examId);
            payload.put("sessionId", sessionId);
            payload.put("score", result.getScore());
            payload.put("totalScore", result.getTotalScore());
            payload.put("grading", false);
            pushService.resultReady(studentId, payload);
        }
    }
}
