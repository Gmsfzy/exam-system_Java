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

    @Transactional(readOnly = true)
    public List<ResultDto.ResultSummary> listMyResults(Long studentId) {
        return resultRepository.findByStudentId(studentId).stream()
                .map(r -> {
                    Exam e = examRepository.findById(r.getExamId()).orElse(null);
                    return new ResultDto.ResultSummary(r.getId(), r.getExamId(),
                            e == null ? "" : e.getTitle(), r.getScore(), r.getTotalScore(),
                            r.getSubmittedAt());
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
                .map(r -> new ResultDto.ResultSummary(r.getId(), r.getExamId(),
                        e.getTitle(), r.getScore(), r.getTotalScore(), r.getSubmittedAt()))
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

        // 加载答题详情
        Optional<ExamSession> sessionOpt = examSessionRepository
                .findByExamIdAndStudentIdAndStatus(exam.getId(), r.getStudentId(),
                        com.exam.backend.domain.enums.SessionStatusEnum.submitted);
        if (sessionOpt.isEmpty()) {
            sessionOpt = examSessionRepository.findByExamIdAndStudentIdAndStatus(
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
                        a.getGradeStatus());
            }).toList();
        }

        return new ResultDto.ResultDetail(r.getId(), r.getExamId(), exam.getTitle(),
                r.getStudentId(), student == null ? null : student.getUsername(),
                r.getScore(), r.getTotalScore(), r.getSubmittedAt(),
                r.getAiAnalysis(), released, r.getGrading(), views);
    }

    @Transactional(readOnly = true)
    public List<ResultDto.GradeStudentView> gradingList(Long examId, Long currentUserId) {
        Exam e = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "考试不存在"));
        if (!e.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅创建教师可访问");
        }
        return resultRepository.findByExamId(examId).stream()
                .map(r -> {
                    User u = userRepository.findById(r.getStudentId()).orElse(null);
                    return new ResultDto.GradeStudentView(
                            r.getStudentId(),
                            u == null ? null : u.getUsername(),
                            e.getTitle(),
                            r.getScore(), r.getTotalScore(), false);
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
                .findByExamIdAndStudentIdAndStatus(examId, studentId,
                        com.exam.backend.domain.enums.SessionStatusEnum.submitted);
        if (session.isEmpty()) {
            session = examSessionRepository.findByExamIdAndStudentIdAndStatus(
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
                .findByExamIdAndStudentIdAndStatus(examId, studentId,
                        com.exam.backend.domain.enums.SessionStatusEnum.submitted);
        if (session.isEmpty()) {
            session = examSessionRepository.findByExamIdAndStudentIdAndStatus(
                    examId, studentId, com.exam.backend.domain.enums.SessionStatusEnum.auto_submitted);
        }
        ExamSession sess = session.orElseThrow(() ->
                new BusinessException(ErrorCode.BUSINESS_ERROR, "无该学生的考试会话"));
        Answer a = answerRepository.findBySessionIdAndQuestionId(sess.getId(), req.questionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "答题记录不存在"));
        a.setManualScore(req.manualScore());
        a.setManualComment(req.manualComment());
        a.setNeedsManualGrade(false);
        // 重算成绩总分
        Result result = resultRepository.findByExamIdAndStudentId(examId, studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "成绩记录不存在"));
        double total = answerRepository.findBySessionId(sess.getId()).stream()
                .map(Answer::effectiveScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        result.setScore(total);
        return new ResultDto.ManualGradeResponse(a.getId(), a.getManualScore(), a.getManualComment());
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
}