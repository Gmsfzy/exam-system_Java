package com.exam.backend.service;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.Answer;
import com.exam.backend.domain.entity.Exam;
import com.exam.backend.domain.entity.ExamQuestion;
import com.exam.backend.domain.entity.ExamSession;
import com.exam.backend.domain.entity.ExamStudent;
import com.exam.backend.domain.entity.Question;
import com.exam.backend.domain.entity.Result;
import com.exam.backend.domain.entity.User;
import com.exam.backend.domain.enums.ExamStatusEnum;
import com.exam.backend.domain.enums.SessionStatusEnum;
import com.exam.backend.dto.AiDto;
import com.exam.backend.dto.ExamDto;
import com.exam.backend.repository.AnswerRepository;
import com.exam.backend.repository.ExamQuestionRepository;
import com.exam.backend.repository.ExamRepository;
import com.exam.backend.repository.ExamSessionRepository;
import com.exam.backend.repository.ExamStudentRepository;
import com.exam.backend.repository.QuestionRepository;
import com.exam.backend.repository.ResultRepository;
import com.exam.backend.repository.UserRepository;
import com.exam.backend.util.TimeZoneUtil;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final QuestionRepository questionRepository;
    private final ExamStudentRepository examStudentRepository;
    private final ExamSessionRepository examSessionRepository;
    private final AnswerRepository answerRepository;
    private final ResultRepository resultRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AiService aiService;

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
                    e.getCreatedAt(),
                    e.resultsPublishedEffective(), e.maxAttemptsEffective(), e.scoreStrategyEffective(),
                    e.paperModeEffective(), e.getRandomCount(), e.shuffleOptionsEffective(),
                    e.multiScoreRuleEffective(), e.anonymousGradingEffective());
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
                .resultsPublished(req.resultsPublished() == null || req.resultsPublished())
                .maxAttempts(req.maxAttempts())
                .scoreStrategy(req.scoreStrategy())
                .paperMode(req.paperMode())
                .randomCount(req.randomCount())
                .shuffleOptions(req.shuffleOptions())
                .multiScoreRule(req.multiScoreRule())
                .anonymousGrading(req.anonymousGrading())
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
        // M6 考务配置：传啥改啥，null 保持原值（补考授权会抬高 maxAttempts）
        if (req.resultsPublished() != null) e.setResultsPublished(req.resultsPublished());
        if (req.maxAttempts() != null) e.setMaxAttempts(Math.max(1, req.maxAttempts()));
        if (req.scoreStrategy() != null) e.setScoreStrategy(req.scoreStrategy());
        if (req.paperMode() != null) e.setPaperMode(req.paperMode());
        if (req.randomCount() != null) e.setRandomCount(Math.max(0, req.randomCount()));
        if (req.shuffleOptions() != null) e.setShuffleOptions(req.shuffleOptions());
        if (req.multiScoreRule() != null) e.setMultiScoreRule(req.multiScoreRule());
        if (req.anonymousGrading() != null) e.setAnonymousGrading(req.anonymousGrading());
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
                e.getCreatedAt(),
                e.resultsPublishedEffective(), e.maxAttemptsEffective(), e.scoreStrategyEffective(),
                e.paperModeEffective(), e.getRandomCount(), e.shuffleOptionsEffective(),
                e.multiScoreRuleEffective(), e.anonymousGradingEffective());
    }

    // ==== M6 考务管理：发布成绩 / 补考授权 / 监考 / 导出 / 试题分析 / AI 质检 ====

    /** 统一发布成绩：置 resultsPublished=true 并逐个通知已交卷学生 */
    @Transactional
    public ExamDto.PublishResultsResponse publishResults(Long examId, Long currentUserId) {
        Exam e = requireOwned(examId, currentUserId);
        e.setResultsPublished(true);
        examRepository.save(e);
        int notified = 0;
        for (ExamStudent es : examStudentRepository.findByExamId(examId)) {
            Result r = resultRepository
                    .findEffective(examId, es.getStudentId(), e.scoreStrategyEffective()).orElse(null);
            if (r == null) continue;
            try {
                notificationService.notifyResultPublished(es.getStudentId(), examId, e.getTitle(), r.getScore());
                notified++;
            } catch (Exception ex) {
                org.slf4j.LoggerFactory.getLogger(ExamService.class)
                        .warn("publish notify failed student={}: {}", es.getStudentId(), ex.getMessage());
            }
        }
        return new ExamDto.PublishResultsResponse(examId, true, notified);
    }

    /** 补考授权：上调考试总次数上限（受 maxAttempts 总上限语义约束） */
    @Transactional
    public ExamDto.GrantAttemptResponse grantAttempt(Long examId, ExamDto.GrantAttemptRequest req, Long currentUserId) {
        Exam e = requireOwned(examId, currentUserId);
        int extra = req.extraAttempts() == null || req.extraAttempts() < 1 ? 1 : req.extraAttempts();
        if (!examStudentRepository.existsByExamIdAndStudentId(examId, req.studentId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该学生未参加本场考试");
        }
        int ceiling = Math.max(e.maxAttemptsEffective(), 100);
        int next = Math.min(e.maxAttemptsEffective() + extra, ceiling);
        e.setMaxAttempts(next);
        examRepository.save(e);
        return new ExamDto.GrantAttemptResponse(examId, req.studentId(), next);
    }

    /** 实时监考：逐人最新会话状态/已答数/总题数/切屏/会话得分（轮询友好） */
    @Transactional(readOnly = true)
    public List<ExamDto.MonitorRow> monitor(Long examId, Long currentUserId) {
        Exam e = requireOwned(examId, currentUserId);
        List<ExamQuestion> eqs = examQuestionRepository.findByExamIdOrderByOrderAsc(examId);
        Map<Long, List<ExamSession>> sessionsByStudent = new HashMap<>();
        for (ExamSession s : examSessionRepository.findByExamId(examId)) {
            sessionsByStudent.computeIfAbsent(s.getStudentId(), k -> new ArrayList<>()).add(s);
        }
        List<ExamDto.MonitorRow> rows = new ArrayList<>();
        for (ExamStudent es : examStudentRepository.findByExamId(examId)) {
            Long sid = es.getStudentId();
            ExamSession latest = sessionsByStudent.getOrDefault(sid, List.of()).stream()
                    .max(Comparator.comparing(ExamSession::getId)).orElse(null);
            int total = latest != null && latest.getAssignedQuestionIds() != null
                    && !latest.getAssignedQuestionIds().isEmpty()
                    ? latest.getAssignedQuestionIds().size() : eqs.size();
            int answered = 0;
            if (latest != null) {
                answered = (int) answerRepository.findBySessionId(latest.getId()).stream()
                        .filter(a -> a.getStudentAnswer() != null && !a.getStudentAnswer().isBlank())
                        .count();
            }
            rows.add(new ExamDto.MonitorRow(sid,
                    userRepository.findById(sid).map(User::getUsername).orElse(null),
                    latest == null ? null : latest.attemptNoEffective(),
                    latest == null ? "not_started" : latest.getStatus().name(),
                    answered, total,
                    latest == null ? 0 : latest.getSwitchCount(),
                    latest == null ? null : latest.getScore(),
                    latest == null ? null : latest.getStartTime()));
        }
        return rows;
    }

    /** 成绩Excel导出：考生 + 逐题得分 + 总分 */
    @Transactional(readOnly = true)
    public byte[] exportResultsExcel(Long examId, Long currentUserId) {
        Exam e = requireOwned(examId, currentUserId);
        List<ExamQuestion> eqs = examQuestionRepository.findByExamIdOrderByOrderAsc(examId);
        List<ExamSession> sessions = new ArrayList<>();
        sessions.addAll(examSessionRepository.findByExamIdAndStatus(examId, SessionStatusEnum.submitted));
        sessions.addAll(examSessionRepository.findByExamIdAndStatus(examId, SessionStatusEnum.auto_submitted));
        Map<Long, ExamSession> latestByStudent = new HashMap<>();
        for (ExamSession s : sessions) {
            latestByStudent.merge(s.getStudentId(), s,
                    (a, b) -> b.getId() > a.getId() ? b : a);
        }
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("成绩");
            XSSFRow head = sheet.createRow(0);
            String[] fixed = {"考生", "轮次"};
            for (int i = 0; i < fixed.length; i++) head.createCell(i).setCellValue(fixed[i]);
            for (int i = 0; i < eqs.size(); i++) {
                head.createCell(2 + i).setCellValue("第" + (i + 1) + "题");
            }
            head.createCell(2 + eqs.size()).setCellValue("总分");
            List<Long> sids = latestByStudent.keySet().stream().sorted().toList();
            Map<Long, List<Answer>> answersBySession = sids.isEmpty() ? Map.of()
                    : answerRepository.findBySessionIdIn(
                            latestByStudent.values().stream().map(ExamSession::getId).toList()).stream()
                            .collect(Collectors.groupingBy(Answer::getSessionId));
            int rowIdx = 1;
            for (Long stuId : sids) {
                ExamSession sess = latestByStudent.get(stuId);
                Map<Long, Answer> byQ = answersBySession.getOrDefault(sess.getId(), List.of()).stream()
                        .collect(Collectors.toMap(Answer::getQuestionId, Function.identity(), (a, b) -> a));
                XSSFRow row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(userRepository.findById(stuId)
                        .map(User::getUsername).orElse("#" + stuId));
                row.createCell(1).setCellValue(sess.attemptNoEffective());
                for (int i = 0; i < eqs.size(); i++) {
                    Answer a = byQ.get(eqs.get(i).getQuestionId());
                    if (a != null && a.effectiveScore() != null) {
                        row.createCell(2 + i).setCellValue(a.effectiveScore());
                    }
                }
                resultRepository.findBySessionId(sess.getId()).stream()
                        .reduce((x, y) -> y).ifPresent(r ->
                                row.createCell(2 + eqs.size()).setCellValue(r.getScore() == null ? 0 : r.getScore()));
            }
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "导出失败: " + ex.getMessage());
        }
    }

    /** M6 试题分析：逐题得分率 / 区分度(高前27%减低后27%得分率) / 干扰项选择率 */
    @Transactional(readOnly = true)
    public List<ExamDto.ItemAnalysisRow> itemAnalysis(Long examId, Long currentUserId) {
        Exam e = requireOwned(examId, currentUserId);
        List<ExamQuestion> eqs = examQuestionRepository.findByExamIdOrderByOrderAsc(examId);
        if (eqs.isEmpty()) return List.of();
        List<ExamSession> sessions = new ArrayList<>();
        sessions.addAll(examSessionRepository.findByExamIdAndStatus(examId, SessionStatusEnum.submitted));
        sessions.addAll(examSessionRepository.findByExamIdAndStatus(examId, SessionStatusEnum.auto_submitted));
        Map<Long, ExamSession> latestByStudent = new HashMap<>();
        for (ExamSession s : sessions) {
            latestByStudent.merge(s.getStudentId(), s, (a, b) -> b.getId() > a.getId() ? b : a);
        }
        if (latestByStudent.isEmpty()) return List.of();
        List<Long> sessionIds = latestByStudent.values().stream().map(ExamSession::getId).toList();
        Map<Long, List<Answer>> answersBySession = answerRepository.findBySessionIdIn(sessionIds).stream()
                .collect(Collectors.groupingBy(Answer::getSessionId));
        // 学生总分（按最新会话）用于分组
        Map<Long, Double> totalBySession = new HashMap<>();
        for (Map.Entry<Long, ExamSession> en : latestByStudent.entrySet()) {
            ExamSession s = en.getValue();
            double tot = s.getScore() != null ? s.getScore()
                    : answersBySession.getOrDefault(s.getId(), List.of()).stream()
                    .map(Answer::effectiveScore).filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue).sum();
            totalBySession.put(s.getId(), tot);
        }
        List<Long> sortedSessions = totalBySession.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey).toList();
        int n = sortedSessions.size();
        int group = Math.max(1, (int) Math.round(n * 0.27));
        Set<Long> high = new HashSet<>(sortedSessions.subList(0, Math.min(group, n)));
        Set<Long> low = new HashSet<>(sortedSessions.subList(Math.max(0, n - group), n));
        List<Long> qids = eqs.stream().map(ExamQuestion::getQuestionId).toList();
        Map<Long, Question> qmap = questionRepository.findAllById(qids).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        List<ExamDto.ItemAnalysisRow> out = new ArrayList<>();
        int order = 0;
        for (ExamQuestion eq : eqs) {
            order++;
            Question q = qmap.get(eq.getQuestionId());
            double max = eq.getScore() == null ? 0 : eq.getScore();
            int answerCount = 0;
            double scoreSum = 0;
            int highGot = 0, lowGot = 0, highN = 0, lowN = 0;
            Map<String, Integer> pickCount = new LinkedHashMap<>();
            List<String> letters = new ArrayList<>();
            if (q != null && q.getOptions() != null) {
                for (int i = 0; i < q.getOptions().size(); i++) letters.add(String.valueOf((char) ('A' + i)));
            }
            letters.forEach(l -> pickCount.put(l, 0));
            for (Map.Entry<Long, ExamSession> en : latestByStudent.entrySet()) {
                Answer a = answersBySession.getOrDefault(en.getValue().getId(), List.of()).stream()
                        .filter(x -> x.getQuestionId().equals(eq.getQuestionId()))
                        .findFirst().orElse(null);
                if (a == null || a.getStudentAnswer() == null || a.getStudentAnswer().isBlank()) continue;
                answerCount++;
                double got = a.effectiveScore() == null || max == 0 ? 0 : a.effectiveScore();
                scoreSum += got;
                boolean picked = got > 0;
                if (high.contains(en.getValue().getId())) { highN++; if (picked) highGot++; }
                if (low.contains(en.getValue().getId())) { lowN++; if (picked) lowGot++; }
                for (String token : a.getStudentAnswer().toUpperCase().split("[,，;；\\s]+")) {
                    String t = token.trim();
                    if (pickCount.containsKey(t)) pickCount.merge(t, 1, Integer::sum);
                }
            }
            double scoreRate = answerCount == 0 || max == 0 ? 0 : scoreSum / (max * answerCount);
            double disc = highN == 0 || lowN == 0 ? 0 : (double) highGot / highN - (double) lowGot / lowN;
            List<Map<String, Object>> distractors = new ArrayList<>();
            Set<String> correctSet = new HashSet<>();
            if (q != null && q.getAnswer() != null) {
                for (String s : q.getAnswer().toUpperCase().split("[,，;；\\s]+")) {
                    if (!s.isBlank()) correctSet.add(s.trim());
                }
            }
            for (Map.Entry<String, Integer> de : pickCount.entrySet()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("option", de.getKey());
                m.put("rate", answerCount == 0 ? 0 : (double) de.getValue() / answerCount);
                m.put("isCorrect", correctSet.contains(de.getKey()));
                distractors.add(m);
            }
            out.add(new ExamDto.ItemAnalysisRow(eq.getQuestionId(), order,
                    q == null ? null : q.getContent(),
                    q == null ? null : q.getType().name(),
                    max, answerCount,
                    Math.round(scoreRate * 1000) / 1000.0,
                    Math.round(disc * 1000) / 1000.0,
                    distractors));
        }
        return out;
    }

    /** M6 AI 试卷质检（限流由现有 AI 路径统一覆盖） */
    public AiDto.AiInspectResponse aiInspect(Long examId, Long currentUserId) {
        Exam e = requireOwned(examId, currentUserId);
        List<Long> qids = examQuestionRepository.findByExamIdOrderByOrderAsc(examId).stream()
                .map(ExamQuestion::getQuestionId).toList();
        if (qids.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "考试无题目");
        }
        List<Question> qs = questionRepository.findAllById(qids);
        return aiService.inspectExam(e.getTitle(), qs);
    }
}