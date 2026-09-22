package com.exam.backend.service;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.*;
import com.exam.backend.domain.enums.ExamStatusEnum;
import com.exam.backend.domain.enums.MultiScoreRuleEnum;
import com.exam.backend.domain.enums.QuestionTypeEnum;
import com.exam.backend.domain.enums.SessionStatusEnum;
import com.exam.backend.dto.ExamExecutionDto;
import com.exam.backend.repository.*;
import com.exam.backend.service.learning.StudyLogService;
import com.exam.backend.service.learning.WrongAnswerService;
import com.exam.backend.util.AnswerComparator;
import com.exam.backend.util.ExamScoringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamExecutionService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamSessionRepository examSessionRepository;
    private final ExamStudentRepository examStudentRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final ResultRepository resultRepository;
    private final AnswerComparator answerComparator;
    private final ExamScoringUtil examScoringUtil;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final WrongAnswerService wrongAnswerService;
    private final StudyLogService studyLogService;

    @Transactional
    public ExamExecutionDto.StartExamResponse start(Long examId, Long studentId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "考试不存在"));
        if (exam.getStatus() != ExamStatusEnum.published) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "考试当前不可作答");
        }
        LocalDateTime now = LocalDateTime.now();
        if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "考试尚未开始");
        }
        if (exam.getEndTime() != null && now.isAfter(exam.getEndTime())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "考试已结束");
        }
        if (!examStudentRepository.existsByExamIdAndStudentId(examId, studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您未被邀请参加该考试");
        }
        Optional<ExamSession> existed = examSessionRepository
                .findFirstByExamIdAndStudentIdAndStatusOrderByIdDesc(examId, studentId, SessionStatusEnum.in_progress);
        if (existed.isPresent()) {
            ExamSession session = existed.get();
            return new ExamExecutionDto.StartExamResponse(
                    session.getId(), session.getExamId(), session.getStudentId(),
                    session.getStartTime(), session.getStatus());
        }
        long activeCount = examSessionRepository
                .findByExamIdAndStatus(examId, SessionStatusEnum.in_progress).stream()
                .filter(s -> s.getStudentId().equals(studentId))
                .count();
        if (activeCount > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已存在进行中的考试会话,请勿重复开始");
        }
        // M6 考次校验：已用轮次不得超过考试次数上限（补考授权通过上调 maxAttempts 实现）
        long usedAttempts = examSessionRepository.findByExamIdAndStudentId(examId, studentId).size();
        if (usedAttempts >= exam.maxAttemptsEffective()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "考试次数已达上限(" + exam.maxAttemptsEffective() + "次)，如需补考请联系教师授权");
        }
        // M6 组卷：random 模式固化个人抽题卷；shuffleOptions 时生成会话级选项乱序映射
        List<ExamQuestion> eqs = examQuestionRepository.findByExamIdOrderByOrderAsc(examId);
        if (eqs.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "考试无题目");
        List<Long> assigned = buildAssignedQuestionIds(exam, eqs);
        Map<Long, Question> qmap = assigned.isEmpty() ? Map.of()
                : questionRepository.findAllById(assigned).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<String, Object> optionMap = exam.shuffleOptionsEffective()
                ? buildOptionMap(assigned, qmap) : Map.of();
        ExamSession session = ExamSession.builder()
                .examId(examId)
                .studentId(studentId)
                .startTime(LocalDateTime.now())
                .status(SessionStatusEnum.in_progress)
                .switchCount(0)
                .attemptNo((int) usedAttempts + 1)
                .assignedQuestionIds(assigned)
                .optionMap(optionMap)
                .build();
        session = examSessionRepository.save(session);
        return new ExamExecutionDto.StartExamResponse(
                session.getId(), session.getExamId(), session.getStudentId(),
                session.getStartTime(), session.getStatus());
    }

    @Transactional(readOnly = true)
    public ExamExecutionDto.TakeExamResponse take(Long examId, Long studentId) {
        ExamSession session = requireActiveSession(examId, studentId);
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "考试不存在"));
        // M6：按会话固化的个人卷出题，选项按 optionMap 乱序下发（学生端零感知）
        List<ExamQuestion> eqs = examQuestionRepository.findByExamIdOrderByOrderAsc(examId);
        if (eqs.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "考试无题目");
        List<Long> assigned = sessionAssignedIds(session, eqs);
        Map<Long, Question> qmap = questionRepository.findAllById(assigned).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        List<Question> ordered = assigned.stream().map(qmap::get).filter(Objects::nonNull).toList();
        if (ordered.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "考试无题目");
        Map<String, Object> optionMap = session.getOptionMap();
        List<Answer> existing = answerRepository.findBySessionId(session.getId());
        Map<Long, Answer> amap = existing.stream()
                .collect(Collectors.toMap(Answer::getQuestionId, Function.identity()));

        int idx = 0;
        Map<Long, ExamQuestion> eqById = eqs.stream()
                .collect(Collectors.toMap(ExamQuestion::getQuestionId, Function.identity(), (a, b) -> a));
        List<ExamExecutionDto.TakeQuestionView> views = new ArrayList<>();
        for (Question q : ordered) {
            idx++;
            Answer a = amap.get(q.getId());
            String saved = a == null ? null : a.getStudentAnswer();
            String displayAnswer = examScoringUtil.originalToDisplay(q.getId(), saved, optionMap);
            ExamQuestion eq = eqById.get(q.getId());
            views.add(new ExamExecutionDto.TakeQuestionView(
                    q.getId(), q.getContent(), shuffleQuestionOptions(q, optionMap),
                    q.getType(), q.getDifficulty(),
                    eq == null || eq.getScore() == null ? 10 : eq.getScore(),
                    idx,
                    displayAnswer,
                    displayAnswer != null && !displayAnswer.isBlank(),
                    q.getMaterial(), q.getMaterialGroup()));
        }

        return new ExamExecutionDto.TakeExamResponse(
                session.getId(), session.getExamId(), exam.getTitle(),
                session.getStartTime(), exam.getEndTime(), exam.getDuration(),
                views);
    }

    @Transactional
    public ExamExecutionDto.SaveAnswerResponse saveAnswer(Long examId, Long studentId,
                                                            ExamExecutionDto.SaveAnswerRequest req) {
        ExamSession session = requireActiveSession(examId, studentId);
        // v3.4 归属校验：题目必须属于本场考试，防止给题外题目写答案
        if (!examQuestionRepository.existsById(new ExamQuestion.PK(examId, req.questionId()))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该题目不属于本场考试");
        }
        Answer a = answerRepository.findBySessionIdAndQuestionId(session.getId(), req.questionId())
                .orElseGet(() -> {
                    Answer na = Answer.builder()
                            .sessionId(session.getId())
                            .questionId(req.questionId())
                            .needsManualGrade(false)
                            .build();
                    return na;
                });
        a.setStudentAnswer(req.studentAnswer());
        answerRepository.save(a);
        return new ExamExecutionDto.SaveAnswerResponse(a.getId(), req.questionId(), true);
    }

    @Transactional
    public ExamExecutionDto.SubmitResponse submit(Long examId, Long studentId) {
        ExamSession session = requireActiveSession(examId, studentId);
        if (session.getStatus() == SessionStatusEnum.submitted || session.getStatus() == SessionStatusEnum.auto_submitted) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "考试已提交,无需重复提交");
        }
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "考试不存在"));
        if (exam.getDuration() != null && exam.getDuration() > 0 && session.getStartTime() != null) {
            long elapsedMinutes = java.time.Duration.between(session.getStartTime(), LocalDateTime.now()).toMinutes();
            if (elapsedMinutes > exam.getDuration()) {
                log.warn("Student {} submitted exam {} after duration limit ({} > {} min)",
                        studentId, examId, elapsedMinutes, exam.getDuration());
            }
        }
        Result result = finalizeSubmission(examId, studentId, session, SessionStatusEnum.submitted);
        return new ExamExecutionDto.SubmitResponse(
                session.getId(), result.getId(), result.getScore(), result.getTotalScore(),
                result.getSubmittedAt(), session.getStatus(), result.getGrading(),
                session.attemptNoEffective(), exam.resultsPublishedEffective());
    }

    @Transactional
    public ExamExecutionDto.ReportSwitchResponse reportSwitch(Long examId, Long studentId) {
        ExamSession session = requireActiveSession(examId, studentId);
        int next = (session.getSwitchCount() == null ? 0 : session.getSwitchCount()) + 1;
        session.setSwitchCount(next);
        if (next >= 3) {
            finalizeSubmission(examId, studentId, session, SessionStatusEnum.auto_submitted);
            return new ExamExecutionDto.ReportSwitchResponse(next, true);
        }
        return new ExamExecutionDto.ReportSwitchResponse(next, false);
    }

    @Transactional(readOnly = true)
    public ExamExecutionDto.ExamReportResponse report(Long examId, Long studentId) {
        Optional<ExamSession> sessionOpt = examSessionRepository
                .findFirstByExamIdAndStudentIdAndStatusOrderByIdDesc(examId, studentId, SessionStatusEnum.submitted);
        if (sessionOpt.isEmpty()) {
            sessionOpt = examSessionRepository.findFirstByExamIdAndStudentIdAndStatusOrderByIdDesc(
                    examId, studentId, SessionStatusEnum.auto_submitted);
        }
        ExamSession session = sessionOpt.orElseThrow(() ->
                new BusinessException(ErrorCode.BUSINESS_ERROR, "尚未交卷,无法查看报告"));
        Exam exam = examRepository.findById(examId).orElseThrow();
        Result result = resultRepository.findEffective(examId, studentId, exam.scoreStrategyEffective())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "成绩记录不存在"));

        boolean released = exam.getStatus() == ExamStatusEnum.ended
                || (exam.getEndTime() != null && LocalDateTime.now().isAfter(exam.getEndTime()));

        List<Answer> answers = answerRepository.findBySessionId(session.getId());
        List<Long> qids = answers.stream().map(Answer::getQuestionId).toList();
        Map<Long, Question> qmap = qids.isEmpty() ? Map.of()
                : questionRepository.findAllById(qids).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        List<ExamExecutionDto.AnswerView> views = answers.stream()
                .map(a -> {
                    Question q = qmap.get(a.getQuestionId());
                    String correct = released && q != null ? safe(q.getAnswer()) : "";
                    String analysis = released && q != null ? safe(q.getAnalysis()) : "";
                    return new ExamExecutionDto.AnswerView(
                            a.getQuestionId(),
                            q == null ? null : q.getContent(),
                            q == null ? null : q.getType(),
                            a.getStudentAnswer(),
                            correct,
                            analysis,
                            a.getIsCorrect(),
                            a.effectiveScore(),
                            getScoreMax(examId, a.getQuestionId()),
                            a.getNeedsManualGrade(),
                            a.getGradeStatus(),
                            released && q != null ? q.getMaterial() : null,
                            q == null ? null : q.getMaterialGroup());
                })
                .toList();

        return new ExamExecutionDto.ExamReportResponse(
                session.getId(), result.getId(), result.getScore(), result.getTotalScore(),
                session.getStatus(), result.getGrading(), views,
                session.attemptNoEffective(), exam.resultsPublishedEffective());
    }

    /**
     * 核心方法: 完成考试批改并生成成绩
     * - 客观题本地比对（多选按 multiScoreRule 全对/漏选按比例）
     * - 学生选项先经会话 optionMap 映射回原字母再判分
     * - 主观题 AI 批改（clamp [0, maxScore]）
     * - 生成/更新 Result 记录（best/last 策略 upsert）
     * - 发送通知（受成绩发布门控）
     */
    @Transactional
    public Result finalizeSubmission(Long examId, Long studentId,
                                       ExamSession session, SessionStatusEnum newStatus) {
        // M6：每轮会话独立成一条成绩；防重复结算以当轮会话已有成绩为准
        Exam exam0 = examRepository.findById(examId).orElse(null);
        List<Result> sessionResults = resultRepository.findBySessionId(session.getId());
        if (!sessionResults.isEmpty() && session.getStatus() != SessionStatusEnum.in_progress) {
            return sessionResults.get(sessionResults.size() - 1);
        }

        session.setStatus(newStatus);
        session.setEndTime(LocalDateTime.now());

        // 加载题目（M6：按会话固化的个人卷范围判分）
        List<ExamQuestion> eqsAll = examQuestionRepository.findByExamIdOrderByOrderAsc(examId);
        Map<Long, ExamQuestion> allEqMap = eqsAll.stream()
                .collect(Collectors.toMap(ExamQuestion::getQuestionId, Function.identity(), (a, b) -> a));
        List<Long> assigned = sessionAssignedIds(session, eqsAll);
        Set<Long> assignedSet = new HashSet<>(assigned);
        List<ExamQuestion> eqs = assignedSet.size() == eqsAll.size() ? eqsAll
                : assigned.stream().map(allEqMap::get).filter(Objects::nonNull).toList();
        Map<Long, Question> qmap = assigned.isEmpty() ? Map.of()
                : questionRepository.findAllById(assigned).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<Long, ExamQuestion> eqMap = eqs.stream()
                .collect(Collectors.toMap(ExamQuestion::getQuestionId, Function.identity(), (a, b) -> a));
        Map<String, Object> optionMap = session.getOptionMap();
        MultiScoreRuleEnum multiRule = exam0 == null
                ? MultiScoreRuleEnum.all_or_nothing : exam0.multiScoreRuleEffective();

        // 加载学生答案
        List<Answer> answers = answerRepository.findBySessionId(session.getId());
        double totalScore = 0.0;
        double totalMax = 0.0;
        boolean hasPending = false;

        for (Answer a : answers) {
            Question q = qmap.get(a.getQuestionId());
            if (q == null) continue;
            // 判分前先映射回原题未提交答案（未乱序时原样返回）
            a.setStudentAnswer(examScoringUtil.displayToOriginal(a.getQuestionId(), a.getStudentAnswer(), optionMap));
            ExamQuestion eq = eqMap.get(a.getQuestionId());
            double maxScore = eq == null || eq.getScore() == null ? 0 : eq.getScore();
            totalMax += maxScore;

            if (QuestionTypeEnum.isSubjective(q.getType())) {
                // 主观题: 仅标记待 AI 异步判分（事务内绝不调用 AI），得分由后台判分回写
                if (a.getStudentAnswer() == null || a.getStudentAnswer().isBlank()) continue;
                a.setGradeStatus(Answer.PENDING_AI);
                a.setNeedsManualGrade(false);
                hasPending = true;
            } else if (q.getType() == QuestionTypeEnum.multiple_choice) {
                // M6 多选：支持 partial 漏选按比例得分
                double got = examScoringUtil.scoreMultiChoice(q.getAnswer(), a.getStudentAnswer(), maxScore, multiRule);
                boolean correct = got >= maxScore - 1e-9;
                a.setIsCorrect(correct);
                a.setScore(com.exam.backend.service.competition.ScoringService.round2(got));
                a.setGradeStatus(Answer.GRADED);
                totalScore += got;
            } else {
                // 客观题: 本地即时判分
                boolean correct = answerComparator.isCorrect(q.getType(), q.getAnswer(), a.getStudentAnswer());
                a.setIsCorrect(correct);
                a.setScore(correct ? maxScore : 0.0);
                a.setGradeStatus(Answer.GRADED);
                totalScore += correct ? maxScore : 0;
            }
            answerRepository.save(a);
        }

        // 生成成绩记录：有主观题待判时 score 仅为客观题小计，grading=true 待异步补全
        Result result = sessionResults.isEmpty() ? null
                : sessionResults.get(sessionResults.size() - 1);
        if (result == null) {
            result = Result.builder()
                    .examId(examId)
                    .studentId(studentId)
                    .sessionId(session.getId())
                    .build();
        }
        result.setScore(com.exam.backend.service.competition.ScoringService.round2(totalScore));
        result.setTotalScore(totalMax);
        result.setSubmittedAt(LocalDateTime.now());
        result.setGrading(hasPending);
        // best 策略无需覆盖：findEffective(best) 自然取各轮最高分，本轮低分不影响有效成绩
        session.setScore(result.getScore());
        examSessionRepository.save(session);
        resultRepository.save(result);

        // v4.0 自学钩子：错题收录 + 学习日志（异常不阻塞考试主流程）
        try {
            Map<Long, Double> maxScoreMap = new HashMap<>();
            int objectiveCorrect = 0;
            for (ExamQuestion eq : eqs) {
                maxScoreMap.put(eq.getQuestionId(), eq.getScore() == null ? 0.0 : eq.getScore());
            }
            for (Answer a : answers) {
                if (Boolean.TRUE.equals(a.getIsCorrect())) objectiveCorrect++;
            }
            wrongAnswerService.syncFromExam(studentId, examId, answers, qmap, maxScoreMap);
            studyLogService.writeExamLog(studentId, examId, eqs.size(), objectiveCorrect,
                    totalScore, totalMax, session.getStartTime(), session.getEndTime());
        } catch (Exception e) {
            log.warn("learning hooks failed after submit: user={} exam={} err={}",
                    studentId, examId, e.getMessage());
        }

        // 主观题待判：发布事件，事务提交后由专用线程池异步 AI 判分（DB 事务绝不横跨 AI 调用）
        final boolean pending = hasPending;
        if (pending) {
            eventPublisher.publishEvent(new SubjectiveGradingRequested(session.getId()));
        }

        // 通知（事务提交后发送，避免回滚导致数据不一致；M6：未发布成绩仅交卷不公布分数）
        final double finalScore = totalScore;
        final boolean published = exam0 == null || exam0.resultsPublishedEffective();
        try {
            Exam exam = exam0;
            if (exam != null) {
                String examTitle = exam.getTitle();
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                if (pending) {
                                    notificationService.create(studentId, "已交卷",
                                            "您的考试 " + examTitle + " 已交卷，主观题正在 AI 评分中，成绩稍后公布。",
                                            com.exam.backend.domain.enums.NotificationTypeEnum.info, "result", examId);
                                } else if (published) {
                                    notificationService.notifyResultPublished(studentId, examId, examTitle, finalScore);
                                }
                            } catch (Exception e) {
                                log.warn("notify after commit failed: {}", e.getMessage());
                            }
                        }
                    });
                } else {
                    if (pending) {
                        notificationService.create(studentId, "已交卷",
                                "您的考试 " + examTitle + " 已交卷，主观题正在 AI 评分中，成绩稍后公布。",
                                com.exam.backend.domain.enums.NotificationTypeEnum.info, "result", examId);
                    } else if (published) {
                        notificationService.notifyResultPublished(studentId, examId, examTitle, finalScore);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("notify registration failed: {}", e.getMessage());
        }
        return result;
    }

    private ExamSession requireActiveSession(Long examId, Long studentId) {
        return examSessionRepository
                .findFirstByExamIdAndStudentIdAndStatusOrderByIdDesc(examId, studentId, SessionStatusEnum.in_progress)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "无进行中的考试会话,请先 POST /api/exam/{id}/start"));
    }

    // ==== M6 组卷/乱序/多选判分辅助 ====

    /** random 模式抽题（0=全部）并按试卷原题序排列；unified 返回全部 */
    private List<Long> buildAssignedQuestionIds(Exam exam, List<ExamQuestion> eqs) {
        List<Long> all = eqs.stream().map(ExamQuestion::getQuestionId).toList();
        if (exam.paperModeEffective() != com.exam.backend.domain.enums.PaperModeEnum.random) return all;
        int n = Math.min(exam.randomCountOrAll(), all.size());
        List<Long> pool = new ArrayList<>(all);
        Collections.shuffle(pool, new Random());
        Set<Long> picked = new HashSet<>(pool.subList(0, n));
        return all.stream().filter(picked::contains).toList();
    }

    /** 会话实际出题范围：有固化个人卷用之，否则全卷 */
    private List<Long> sessionAssignedIds(ExamSession session, List<ExamQuestion> eqs) {
        List<Long> assigned = session.getAssignedQuestionIds();
        if (assigned == null || assigned.isEmpty()) {
            return eqs.stream().map(ExamQuestion::getQuestionId).toList();
        }
        Set<Long> valid = eqs.stream().map(ExamQuestion::getQuestionId).collect(Collectors.toSet());
        return assigned.stream().filter(valid::contains).toList();
    }

    /** 对单选/多选（有选项且≥2个）生成 显示字母->原字母 映射，以 “qid:显示字母” 为键存入扁平 Map */
    private Map<String, Object> buildOptionMap(List<Long> qids, Map<Long, Question> qmap) {
        Map<String, Object> result = new LinkedHashMap<>();
        Random rnd = new Random();
        for (Long qid : qids) {
            Question q = qmap.get(qid);
            if (q == null || q.getOptions() == null || q.getOptions().size() < 2) continue;
            if (q.getType() != QuestionTypeEnum.single_choice && q.getType() != QuestionTypeEnum.multiple_choice) continue;
            int n = q.getOptions().size();
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < n; i++) order.add(i);
            Collections.shuffle(order, rnd);
            boolean identity = true;
            Map<String, String> map = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) {
                map.put(examScoringUtil.letter(i), examScoringUtil.letter(order.get(i)));
                identity &= i == order.get(i);
            }
            if (!identity) result.putAll(map.entrySet().stream()
                    .collect(Collectors.toMap(e -> qid + ":" + e.getKey(), Map.Entry::getValue)));
        }
        return result;
    }

    /** 按 optionMap 重排选项内容下发；未乱序返回原选项 */
    private List<String> shuffleQuestionOptions(Question q, Map<String, Object> optionMap) {
        Map<String, String> m = examScoringUtil.subMap(q.getId(), optionMap);
        List<String> options = q.getOptions();
        if (m == null || options == null || options.isEmpty()) return options;
        List<String> shuffled = new ArrayList<>(options.size());
        for (int i = 0; i < options.size(); i++) {
            String orig = m.getOrDefault(examScoringUtil.letter(i), examScoringUtil.letter(i));
            int oi = orig.charAt(0) - 'A';
            shuffled.add(oi >= 0 && oi < options.size() ? options.get(oi) : options.get(i));
        }
        return shuffled;
    }

    private Double getScoreMax(Long examId, Long questionId) {
        return examQuestionRepository.findById(new ExamQuestion.PK(examId, questionId))
                .map(ExamQuestion::getScore)
                .map(Double::valueOf)
                .orElse(0.0);
    }

    private String safe(String s) { return s == null ? "" : s; }
}