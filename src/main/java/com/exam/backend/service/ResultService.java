package com.exam.backend.service;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.*;
import com.exam.backend.domain.enums.ExamStatusEnum;
import com.exam.backend.domain.enums.QuestionTypeEnum;
import com.exam.backend.dto.ExamExecutionDto;
import com.exam.backend.dto.ResultDto;
import com.exam.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResultService {

    private final ResultRepository resultRepository;
    private final ExamRepository examRepository;
    private final ExamSessionRepository examSessionRepository;
    private final AnswerRepository answerRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<ResultDto.ResultSummary> listMyResults(Long studentId) {
        // M6：多轮次全部展示（标轮次）；未发布成绩的考试总分置 null + published=false
        return resultRepository.findByStudentId(studentId).stream()
                .map(r -> {
                    Exam e = examRepository.findById(r.getExamId()).orElse(null);
                    boolean published = e == null || e.resultsPublishedEffective();
                    Integer attemptNo = r.getSessionId() == null ? 1 : examSessionRepository
                            .findById(r.getSessionId()).map(ExamSession::attemptNoEffective).orElse(1);
                    return new ResultDto.ResultSummary(r.getId(), r.getExamId(),
                            e == null ? "" : e.getTitle(),
                            published ? r.getScore() : null,
                            published ? r.getTotalScore() : null,
                            r.getSubmittedAt(),
                            attemptNo, r.getSessionId(), published, r.reviewStatusEffective());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ResultDto.ResultSummary> listExamResults(Long examId, Long currentUserId, String role) {
        Exam e = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "考试不存在"));
        if (!"teacher".equalsIgnoreCase(role) || !e.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅考试创建教师可查看所有成绩");
        }
        return resultRepository.findByExamId(examId).stream()
                .map(r -> {
                    Integer attemptNo = r.getSessionId() == null ? 1 : examSessionRepository
                            .findById(r.getSessionId()).map(ExamSession::attemptNoEffective).orElse(1);
                    return new ResultDto.ResultSummary(r.getId(), r.getExamId(),
                            e.getTitle(), r.getScore(), r.getTotalScore(), r.getSubmittedAt(),
                            attemptNo, r.getSessionId(), true, r.reviewStatusEffective());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ResultDto.ResultDetail detail(Long resultId, Long currentUserId, String role) {
        Result r = resultRepository.findById(resultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "成绩不存在"));
        Exam exam = examRepository.findById(r.getExamId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "考试不存在"));
        User student = userRepository.findById(r.getStudentId()).orElse(null);
        boolean isTeacher = "teacher".equalsIgnoreCase(role) && exam.getCreatorId().equals(currentUserId);
        boolean isOwner = r.getStudentId().equals(currentUserId);
        if (!isTeacher && !isOwner) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看他人成绩");
        }
        boolean released = isTeacher
                || exam.getStatus() == ExamStatusEnum.ended
                || (exam.getEndTime() != null && java.time.LocalDateTime.now().isAfter(exam.getEndTime()));
        // M6 发布门控：学生视角下未发布则总分为 null
        boolean published = isTeacher || exam.resultsPublishedEffective();

        // 加载答题详情（M6：优先按成绩归属会话取，回退到最新已交卷会话）
        Optional<ExamSession> sessionOpt = r.getSessionId() != null
                ? examSessionRepository.findById(r.getSessionId())
                : Optional.<ExamSession>empty();
        if (sessionOpt.isEmpty()) {
            sessionOpt = examSessionRepository
                    .findFirstByExamIdAndStudentIdAndStatusOrderByIdDesc(exam.getId(), r.getStudentId(),
                            com.exam.backend.domain.enums.SessionStatusEnum.submitted);
        }
        if (sessionOpt.isEmpty()) {
            sessionOpt = examSessionRepository.findFirstByExamIdAndStudentIdAndStatusOrderByIdDesc(
                    exam.getId(), r.getStudentId(),
                    com.exam.backend.domain.enums.SessionStatusEnum.auto_submitted);
        }
        List<ExamExecutionDto.AnswerView> views = List.of();
        if (sessionOpt.isPresent()) {
            ExamSession session = sessionOpt.get();
            List<Answer> answers = answerRepository.findBySessionId(session.getId());
            List<Long> qids = answers.stream().map(Answer::getQuestionId).toList();
            Map<Long, Question> qmap = qids.isEmpty() ? Map.of()
                    : questionRepository.findAllById(qids).stream()
                    .collect(Collectors.toMap(Question::getId, Function.identity()));
            views = answers.stream().map(a -> {
                Question q = qmap.get(a.getQuestionId());
                String correct = released && q != null ? safe(q.getAnswer()) : "";
                String analysis = released && q != null ? safe(q.getAnalysis()) : "";
                Double max = examQuestionRepository.findById(new ExamQuestion.PK(exam.getId(), a.getQuestionId()))
                        .map(ExamQuestion::getScore).map(Double::valueOf).orElse(0.0);
                return new ExamExecutionDto.AnswerView(
                        a.getQuestionId(),
                        q == null ? null : q.getContent(),
                        q == null ? null : q.getType(),
                        a.getStudentAnswer(),
                        correct, analysis,
                        a.getIsCorrect(),
                        a.effectiveScore(),
                        max,
                        a.getNeedsManualGrade(),
                        a.getGradeStatus(),
                        q == null ? null : q.getMaterial(),
                        q == null ? null : q.getMaterialGroup());
            }).toList();
        }

        Integer attemptNo = sessionOpt.map(ExamSession::attemptNoEffective).orElse(1);
        return new ResultDto.ResultDetail(r.getId(), r.getExamId(), exam.getTitle(),
                r.getStudentId(), student == null ? null : student.getUsername(),
                published ? r.getScore() : null, published ? r.getTotalScore() : null,
                r.getSubmittedAt(),
                r.getAiAnalysis(), released, r.getGrading(), views,
                attemptNo, published, r.reviewStatusEffective(),
                r.getReviewReason(), r.getReviewReply(), r.getReviewedAt());
    }

    @Transactional(readOnly = true)
    public List<ResultDto.GradeStudentView> gradingList(Long examId, Long currentUserId) {
        Exam e = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "考试不存在"));
        if (!e.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅创建教师可访问");
        }
        // M6 匿名阅卷：化名下发（教师提交评分仍按 studentId 路由）；同一学生多轮取最新
        boolean anon = e.anonymousGradingEffective();
        Map<Long, Result> latestByStudent = new LinkedHashMap<>();
        for (Result r : resultRepository.findByExamId(examId)) {
            latestByStudent.merge(r.getStudentId(), r,
                    (a, b) -> b.getId() >= a.getId() ? b : a);
        }
        List<Long> orderedStudents = latestByStudent.keySet().stream().sorted().toList();
        Map<Long, String> alias = new HashMap<>();
        for (int i = 0; i < orderedStudents.size(); i++) {
            alias.put(orderedStudents.get(i), "考生" + String.format("%02d", i + 1));
        }
        return latestByStudent.values().stream()
                .map(r -> {
                    User u = userRepository.findById(r.getStudentId()).orElse(null);
                    String name = anon ? alias.get(r.getStudentId())
                            : (u == null ? null : u.getUsername());
                    Integer attemptNo = r.getSessionId() == null ? 1 : examSessionRepository
                            .findById(r.getSessionId()).map(ExamSession::attemptNoEffective).orElse(1);
                    boolean hasPending = answerRepository.findBySessionId(
                            r.getSessionId() == null ? -1L : r.getSessionId()).stream()
                            .anyMatch(a -> Boolean.TRUE.equals(a.getNeedsManualGrade()));
                    return new ResultDto.GradeStudentView(
                            r.getStudentId(), name, e.getTitle(),
                            r.getScore(), r.getTotalScore(), hasPending, attemptNo);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ResultDto.GradeableAnswerView> gradingDetail(Long examId, Long studentId, Long currentUserId) {
        Exam e = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "考试不存在"));
        if (!e.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅创建教师可访问");
        }
        Optional<ExamSession> session = examSessionRepository
                .findFirstByExamIdAndStudentIdAndStatusOrderByIdDesc(examId, studentId,
                        com.exam.backend.domain.enums.SessionStatusEnum.submitted);
        if (session.isEmpty()) {
            session = examSessionRepository.findFirstByExamIdAndStudentIdAndStatusOrderByIdDesc(
                    examId, studentId, com.exam.backend.domain.enums.SessionStatusEnum.auto_submitted);
        }
        if (session.isEmpty()) return List.of();
        List<Answer> answers = answerRepository.findBySessionId(session.get().getId());
        List<Long> qids = answers.stream().map(Answer::getQuestionId).toList();
        Map<Long, Question> qmap = qids.isEmpty() ? Map.of()
                : questionRepository.findAllById(qids).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        return answers.stream()
                .filter(a -> {
                    Question q = qmap.get(a.getQuestionId());
                    return q != null && QuestionTypeEnum.isSubjective(q.getType());
                })
                .map(a -> {
                    Question q = qmap.get(a.getQuestionId());
                    return new ResultDto.GradeableAnswerView(
                            a.getId(), a.getQuestionId(), q.getContent(),
                            q.getType(), a.getStudentAnswer(), q.getAnswer(),
                            a.getAiScore(), a.getAiAnalysis(),
                            a.getManualScore(), a.getManualComment());
                })
                .toList();
    }

    @Transactional
    public ResultDto.ManualGradeResponse manualGrade(Long examId, Long studentId,
                                                      ResultDto.ManualGradeRequest req, Long currentUserId) {
        Exam e = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "考试不存在"));
        if (!e.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅创建教师可评分");
        }
        Optional<ExamSession> session = examSessionRepository
                .findFirstByExamIdAndStudentIdAndStatusOrderByIdDesc(examId, studentId,
                        com.exam.backend.domain.enums.SessionStatusEnum.submitted);
        if (session.isEmpty()) {
            session = examSessionRepository.findFirstByExamIdAndStudentIdAndStatusOrderByIdDesc(
                    examId, studentId, com.exam.backend.domain.enums.SessionStatusEnum.auto_submitted);
        }
        ExamSession sess = session.orElseThrow(() ->
                new BusinessException(ErrorCode.BUSINESS_ERROR, "无该学生的考试会话"));
        Answer a = answerRepository.findBySessionIdAndQuestionId(sess.getId(), req.questionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "答题记录不存在"));
        a.setManualScore(req.manualScore());
        a.setManualComment(req.manualComment());
        a.setNeedsManualGrade(false);
        // 重算成绩总分（M6：优先按会话回写当轮成绩）
        Result result = findResultForSession(examId, studentId, sess.getId());
        if (result == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "成绩记录不存在");
        }
        double total = answerRepository.findBySessionId(sess.getId()).stream()
                .map(Answer::effectiveScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        result.setScore(total);
        return new ResultDto.ManualGradeResponse(a.getId(), a.getManualScore(), a.getManualComment());
    }

    /** M6：取会话对应成绩，存量数据无 sessionId 时回退有效成绩 */
    private Result findResultForSession(Long examId, Long studentId, Long sessionId) {
        Result bySession = resultRepository.findBySessionId(sessionId).stream()
                .reduce((x, y) -> y).orElse(null);
        if (bySession != null) return bySession;
        Exam exam = examRepository.findById(examId).orElse(null);
        return resultRepository.findEffective(examId, studentId,
                exam == null ? com.exam.backend.domain.enums.ScoreStrategyEnum.last
                        : exam.scoreStrategyEffective()).orElse(null);
    }

    @Transactional(readOnly = true)
    public ResultDto.AnalysisResponse analyze(Long examId, Long currentUserId) {
        Exam e = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "考试不存在"));
        if (!e.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅创建教师可分析");
        }
        List<Result> results = resultRepository.findByExamId(examId);
        int count = results.size();
        if (count == 0) {
            return new ResultDto.AnalysisResponse(examId, e.getTitle(), 0, 0,
                    0.0, 0.0, 0.0, 0.0, 60.0, List.of(), List.of(), List.of());
        }
        double avg = results.stream().mapToDouble(r -> r.getScore() == null ? 0 : r.getScore()).average().orElse(0);
        double max = results.stream().mapToDouble(r -> r.getScore() == null ? 0 : r.getScore()).max().orElse(0);
        double min = results.stream().mapToDouble(r -> r.getScore() == null ? 0 : r.getScore()).min().orElse(0);
        double totalMax = results.get(0).getTotalScore() == null ? 100 : results.get(0).getTotalScore();
        double passLine = totalMax * 0.6;
        long pass = results.stream().filter(r -> (r.getScore() == null ? 0 : r.getScore()) >= passLine).count();
        double passRate = pass * 100.0 / count;

        // 分数分布
        int[] bins = new int[10]; // 0-10, 10-20, ..., 90-100
        for (Result r : results) {
            double ratio = totalMax == 0 ? 0 : (r.getScore() == null ? 0 : r.getScore()) / totalMax;
            int idx = Math.min(9, (int) (ratio * 10));
            bins[idx]++;
        }
        List<Map<String, Object>> dist = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("range", (i * 10) + "-" + ((i + 1) * 10));
            m.put("count", bins[i]);
            dist.add(m);
        }

        // 排名
        List<Map<String, Object>> ranking = new ArrayList<>();
        results.stream()
                .sorted((a, b) -> Double.compare(
                        b.getScore() == null ? 0 : b.getScore(),
                        a.getScore() == null ? 0 : a.getScore()))
                .forEach(r -> {
                    User u = userRepository.findById(r.getStudentId()).orElse(null);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("studentId", r.getStudentId());
                    m.put("studentName", u == null ? null : u.getUsername());
                    m.put("score", r.getScore());
                    ranking.add(m);
                });

        return new ResultDto.AnalysisResponse(examId, e.getTitle(), count, count,
                avg, max, min, passRate, passLine, dist, List.of(), ranking);
    }

    private String safe(String s) { return s == null ? "" : s; }

    // ==== M6 成绩申诉 ====

    /** 学生发起成绩申诉：置 pending 并通知考试创建教师 */
    @Transactional
    public ResultDto.ReviewResponse requestReview(Long resultId, ResultDto.ReviewRequest req, Long studentId) {
        Result r = resultRepository.findById(resultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "成绩不存在"));
        if (!r.getStudentId().equals(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅可申诉本人成绩");
        }
        if (req.reason() == null || req.reason().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请填写申诉理由");
        }
        Exam exam = examRepository.findById(r.getExamId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "考试不存在"));
        if (!exam.resultsPublishedEffective()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "成绩尚未发布，无法申诉");
        }
        String status = r.reviewStatusEffective();
        if (Result.REVIEW_PENDING.equals(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "申诉正在处理中");
        }
        r.setReviewStatus(Result.REVIEW_PENDING);
        r.setReviewReason(req.reason().trim());
        r.setReviewReply(null);
        r.setReviewedAt(null);
        resultRepository.save(r);
        try {
            notificationService.create(exam.getCreatorId(), "成绩申诉",
                    "考生 " + userRepository.findById(studentId).map(User::getUsername).orElse("#" + studentId)
                            + " 对考试「" + exam.getTitle() + "」的成绩提出了申诉，请及时处理。",
                    com.exam.backend.domain.enums.NotificationTypeEnum.info, "exam", exam.getId());
        } catch (Exception e) {
            log.warn("notify review failed: {}", e.getMessage());
        }
        return new ResultDto.ReviewResponse(r.getId(), r.getReviewStatus());
    }

    /** 教师处理申诉：approve 可改分（重算总分）/ reject 附回复，均通知学生 */
    @Transactional
    public ResultDto.ReviewResponse handleReview(Long resultId, ResultDto.ReviewHandleRequest req, Long teacherId) {
        Result r = resultRepository.findById(resultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "成绩不存在"));
        Exam exam = examRepository.findById(r.getExamId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "考试不存在"));
        if (!exam.getCreatorId().equals(teacherId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅创建教师可处理申诉");
        }
        if (!Result.REVIEW_PENDING.equals(r.reviewStatusEffective())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该成绩无待处理申诉");
        }
        boolean approve = "approve".equalsIgnoreCase(req.action());
        if (approve) {
            if (req.newScore() != null) {
                double max = r.getTotalScore() == null ? 100 : r.getTotalScore();
                if (req.newScore() < 0 || req.newScore() > max) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR, "新分数需在 0-" + max + " 之间");
                }
                r.setScore(req.newScore());
            }
            r.setReviewStatus(Result.REVIEW_APPROVED);
        } else if ("reject".equalsIgnoreCase(req.action())) {
            r.setReviewStatus(Result.REVIEW_REJECTED);
        } else {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "action 需为 approve / reject");
        }
        r.setReviewReply(req.reply());
        r.setReviewedAt(LocalDateTime.now());
        resultRepository.save(r);
        try {
            notificationService.create(r.getStudentId(),
                    approve ? "申诉已通过" : "申诉已驳回",
                    "考试「" + exam.getTitle() + "」成绩申诉处理结果："
                            + (req.reply() == null || req.reply().isBlank()
                            ? (approve ? "教师已同意并调整成绩。" : "教师维持原成绩。") : req.reply()),
                    com.exam.backend.domain.enums.NotificationTypeEnum.info, "result", r.getExamId());
        } catch (Exception ex) {
            log.warn("notify review handle failed: {}", ex.getMessage());
        }
        return new ResultDto.ReviewResponse(r.getId(), r.getReviewStatus());
    }

    // ==== M6 聚类批注 / 同题批量给分 ====

    /** 未批主观题按题目聚类（跨全部已交卷会话） */
    @Transactional(readOnly = true)
    public List<ResultDto.ClusterItem> gradingCluster(Long examId, Long currentUserId) {
        Exam e = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "考试不存在"));
        if (!e.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅创建教师可访问");
        }
        List<ExamSession> sessions = examSessionRepository.findByExamIdAndStatus(examId,
                com.exam.backend.domain.enums.SessionStatusEnum.submitted);
        sessions.addAll(examSessionRepository.findByExamIdAndStatus(examId,
                com.exam.backend.domain.enums.SessionStatusEnum.auto_submitted));
        if (sessions.isEmpty()) return List.of();
        Map<Long, ExamSession> sessionMap = sessions.stream()
                .collect(Collectors.toMap(ExamSession::getId, Function.identity(), (a, b) -> a));
        List<Answer> pending = answerRepository.findBySessionIdIn(new ArrayList<>(sessionMap.keySet())).stream()
                .filter(a -> Boolean.TRUE.equals(a.getNeedsManualGrade()))
                .toList();
        if (pending.isEmpty()) return List.of();
        Map<Long, Question> qmap = questionRepository
                .findAllById(pending.stream().map(Answer::getQuestionId).distinct().toList()).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<Long, ExamQuestion> eqmap = examQuestionRepository.findByExamIdOrderByOrderAsc(examId).stream()
                .collect(Collectors.toMap(ExamQuestion::getQuestionId, Function.identity(), (a, b) -> a));
        boolean anon = e.anonymousGradingEffective();
        Map<Long, String> alias = anonymousAlias(examId);
        Map<Long, List<Answer>> byQuestion = pending.stream()
                .collect(Collectors.groupingBy(Answer::getQuestionId, LinkedHashMap::new, Collectors.toList()));
        List<ResultDto.ClusterItem> items = new ArrayList<>();
        byQuestion.forEach((qid, list) -> {
            Question q = qmap.get(qid);
            if (q == null) return;
            Double max = eqmap.get(qid) == null || eqmap.get(qid).getScore() == null
                    ? null : Double.valueOf(eqmap.get(qid).getScore());
            List<ResultDto.ClusterAnswer> answers = list.stream().map(a -> {
                ExamSession sess = sessionMap.get(a.getSessionId());
                Long sid = sess == null ? null : sess.getStudentId();
                String name = sid == null ? null
                        : userRepository.findById(sid).map(User::getUsername).orElse(null);
                String display = anon ? alias.get(sid) : name;
                return new ResultDto.ClusterAnswer(a.getId(), sid,
                        display, a.getStudentAnswer(), a.getAiScore(), a.getAiAnalysis());
            }).toList();
            items.add(new ResultDto.ClusterItem(qid, q.getContent(), q.getType(), max, answers));
        });
        return items;
    }

    /** 同题批量给分：对当前考试未批的该题答卷统一打分并重算各会话成绩 */
    @Transactional
    public ResultDto.BatchGradeResponse batchGradeQuestion(Long examId, ResultDto.BatchGradeRequest req,
                                                            Long currentUserId) {
        Exam e = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "考试不存在"));
        if (!e.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅创建教师可评分");
        }
        Double max = examQuestionRepository.findById(new ExamQuestion.PK(examId, req.questionId()))
                .map(eq -> eq.getScore() == null ? null : Double.valueOf(eq.getScore())).orElse(null);
        if (max == null) throw new BusinessException(ErrorCode.NOT_FOUND, "该题不在本场考试");
        if (req.score() == null || req.score() < 0 || req.score() > max) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "分数需在 0-" + max + " 之间");
        }
        List<ExamSession> sessions = new ArrayList<>(examSessionRepository.findByExamIdAndStatus(examId,
                com.exam.backend.domain.enums.SessionStatusEnum.submitted));
        sessions.addAll(examSessionRepository.findByExamIdAndStatus(examId,
                com.exam.backend.domain.enums.SessionStatusEnum.auto_submitted));
        if (sessions.isEmpty()) return new ResultDto.BatchGradeResponse(req.questionId(), 0);
        List<Answer> targets = answerRepository
                .findBySessionIdIn(sessions.stream().map(ExamSession::getId).toList()).stream()
                .filter(a -> a.getQuestionId().equals(req.questionId()))
                .filter(a -> Boolean.TRUE.equals(a.getNeedsManualGrade())
                        || Answer.FAILED.equals(a.getGradeStatus()))
                .toList();
        Set<Long> touchedSessions = new HashSet<>();
        for (Answer a : targets) {
            a.setManualScore(req.score());
            a.setManualComment(req.comment());
            a.setNeedsManualGrade(false);
            a.setGradeStatus(Answer.GRADED);
            answerRepository.save(a);
            touchedSessions.add(a.getSessionId());
        }
        for (Long sid : touchedSessions) {
            double total = answerRepository.findBySessionId(sid).stream()
                    .map(Answer::effectiveScore).filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue).sum();
            resultRepository.findBySessionId(sid).stream()
                    .reduce((x, y) -> y).ifPresent(r -> {
                        r.setScore(total);
                        r.setGrading(answerRepository.countUngraded(sid) > 0);
                        resultRepository.save(r);
                    });
            examSessionRepository.findById(sid).ifPresent(s -> {
                s.setScore(total);
                examSessionRepository.save(s);
            });
        }
        return new ResultDto.BatchGradeResponse(req.questionId(), targets.size());
    }

    /** 匿名化名表：按参考学生 id 排序生成「考生NN」（与 gradingList 一致） */
    private Map<Long, String> anonymousAlias(Long examId) {
        Map<Long, String> alias = new HashMap<>();
        List<Long> students = resultRepository.findByExamId(examId).stream()
                .map(Result::getStudentId).distinct().sorted().toList();
        for (int i = 0; i < students.size(); i++) {
            alias.put(students.get(i), "考生" + String.format("%02d", i + 1));
        }
        return alias;
    }
}