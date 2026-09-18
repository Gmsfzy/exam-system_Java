package com.exam.backend.service.competition;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.Question;
import com.exam.backend.domain.entity.User;
import com.exam.backend.domain.entity.competition.Competition;
import com.exam.backend.domain.entity.competition.CompetitionAnswer;
import com.exam.backend.domain.entity.competition.CompetitionParticipant;
import com.exam.backend.domain.entity.competition.CompetitionQuestion;
import com.exam.backend.domain.enums.QuestionTypeEnum;
import com.exam.backend.dto.CompetitionDto;
import com.exam.backend.repository.QuestionRepository;
import com.exam.backend.repository.UserRepository;
import com.exam.backend.repository.competition.CompetitionAnswerRepository;
import com.exam.backend.repository.competition.CompetitionParticipantRepository;
import com.exam.backend.repository.competition.CompetitionQuestionRepository;
import com.exam.backend.repository.competition.CompetitionRepository;
import com.exam.backend.util.AnswerComparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 竞赛核心服务 (v4.0)：教师管理 + 学生限时赛参赛 + 惰性状态同步 + 统一积分结算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitionService {

    private static final Set<QuestionTypeEnum> OBJECTIVE_TYPES = Set.of(
            QuestionTypeEnum.single_choice, QuestionTypeEnum.multiple_choice,
            QuestionTypeEnum.fill_blank, QuestionTypeEnum.true_false);

    private final CompetitionRepository competitionRepository;
    private final CompetitionQuestionRepository cqRepository;
    private final CompetitionParticipantRepository participantRepository;
    private final CompetitionAnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final ScoringService scoringService;
    private final GamificationService gamificationService;
    private final LeaderboardService leaderboardService;
    private final RealtimePushService pushService;
    private final AnswerComparator answerComparator;
    private final com.exam.backend.service.NotificationService notificationService;

    // ==================== 管理 ====================

    @Transactional(readOnly = true)
    public List<CompetitionDto.CompetitionView> listForTeacher(Long teacherId) {
        return competitionRepository.findByCreatorIdOrderByIdDesc(teacherId).stream()
                .map(this::toView).toList();
    }

    @Transactional
    public CompetitionDto.CompetitionView create(Long teacherId, CompetitionDto.CompetitionRequest req) {
        Competition c = Competition.builder()
                .title(req.title().trim())
                .description(req.description())
                .competitionType("timed")
                .status("draft")
                .startTime(req.startTime())
                .endTime(req.endTime())
                .duration(req.duration() == null || req.duration() <= 0 ? 15 : req.duration())
                .drawCount(req.drawCount() == null || req.drawCount() < 0 ? 0 : req.drawCount())
                .allowPk(Boolean.TRUE.equals(req.allowPk()))
                .scoringRule(req.scoringRule())
                .creatorId(teacherId)
                .build();
        return toView(competitionRepository.save(c));
    }

    @Transactional(readOnly = true)
    public CompetitionDto.CompetitionDetailVO detail(Long competitionId, Long teacherId) {
        Competition c = requireOwn(competitionId, teacherId);
        List<CompetitionDto.CqAdminView> questions = cqRepository.findByCompetitionIdOrderByOrderAsc(competitionId)
                .stream().map(q -> new CompetitionDto.CqAdminView(q.getId(), q.getQuestionId(), q.getQType(),
                        q.getQContent(), q.getOptions(), q.getAnswer(), q.getScore(), q.getOrder())).toList();
        return new CompetitionDto.CompetitionDetailVO(toView(c), questions);
    }

    @Transactional
    public CompetitionDto.CompetitionView update(Long competitionId, Long teacherId,
                                                 CompetitionDto.CompetitionRequest req) {
        Competition c = requireOwn(competitionId, teacherId);
        if (!"draft".equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅草稿态竞赛可修改核心字段");
        }
        c.setTitle(req.title().trim());
        c.setDescription(req.description());
        c.setStartTime(req.startTime());
        c.setEndTime(req.endTime());
        if (req.duration() != null && req.duration() > 0) c.setDuration(req.duration());
        if (req.drawCount() != null && req.drawCount() >= 0) c.setDrawCount(req.drawCount());
        if (req.allowPk() != null) c.setAllowPk(req.allowPk());
        if (req.scoringRule() != null) c.setScoringRule(req.scoringRule());
        return toView(competitionRepository.save(c));
    }

    @Transactional
    public void delete(Long competitionId, Long teacherId) {
        Competition c = requireOwn(competitionId, teacherId);
        List<CompetitionParticipant> ps = participantRepository.findByCompetitionId(competitionId);
        for (CompetitionParticipant p : ps) answerRepository.deleteByParticipantId(p.getId());
        participantRepository.deleteByCompetitionId(competitionId);
        cqRepository.deleteByCompetitionId(competitionId);
        competitionRepository.delete(c);
    }

    @Transactional
    public int addQuestions(Long competitionId, Long teacherId, List<Long> questionIds) {
        Competition c = requireOwn(competitionId, teacherId);
        if (!"draft".equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅草稿态竞赛可添加题目");
        }
        if (questionIds == null || questionIds.isEmpty()) return 0;
        long existing = cqRepository.countByCompetitionId(competitionId);
        int nextOrder = (int) existing;
        List<CompetitionQuestion> snapshots = new ArrayList<>();
        Map<Long, Question> qmap = questionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        Set<Long> already = cqRepository.findByCompetitionIdOrderByOrderAsc(competitionId).stream()
                .map(CompetitionQuestion::getQuestionId).filter(Objects::nonNull).collect(Collectors.toSet());
        for (Long qid : questionIds) {
            Question q = qmap.get(qid);
            if (q == null) continue;
            if (!OBJECTIVE_TYPES.contains(q.getType())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "竞赛仅支持客观题: " + qid);
            }
            if (already.contains(qid)) continue; // 去重
            snapshots.add(CompetitionQuestion.builder()
                    .competitionId(competitionId).questionId(q.getId())
                    .qType(q.getType()).qContent(q.getContent()).options(q.getOptions())
                    .answer(q.getAnswer()).score(10.0).order(nextOrder++).build());
        }
        cqRepository.saveAll(snapshots);
        return snapshots.size();
    }

    @Transactional
    public void removeQuestion(Long competitionId, Long cqId, Long teacherId) {
        requireOwn(competitionId, teacherId);
        CompetitionQuestion cq = cqRepository.findByIdAndCompetitionId(cqId, competitionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
        cqRepository.delete(cq);
    }

    @Transactional
    public CompetitionDto.CompetitionView publish(Long competitionId, Long teacherId) {
        Competition c = requireOwn(competitionId, teacherId);
        if (!"draft".equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅草稿态竞赛可发布");
        }
        if (c.getStartTime() == null || c.getEndTime() == null || !c.getStartTime().isBefore(c.getEndTime())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "发布前需设置有效的时间窗");
        }
        List<CompetitionQuestion> qs = cqRepository.findByCompetitionIdOrderByOrderAsc(competitionId);
        if (qs.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "发布前至少需要一道题目");
        }
        double total = qs.stream().mapToDouble(q -> q.getScore() == null ? 0 : q.getScore()).sum();
        c.setTotalScore(ScoringService.round2(total));
        c.setStatus("published");
        return toView(competitionRepository.save(c));
    }

    @Transactional
    public CompetitionDto.CompetitionView end(Long competitionId, Long teacherId) {
        Competition c = requireOwn(competitionId, teacherId);
        if ("ended".equals(c.getStatus())) return toView(c);
        c.setStatus("ended");
        competitionRepository.save(c);
        settleCompetitionPoints(competitionId);
        return toView(c);
    }

    @Transactional(readOnly = true)
    public List<CompetitionDto.ParticipantView> participants(Long competitionId, Long teacherId) {
        requireOwn(competitionId, teacherId);
        List<CompetitionParticipant> ps = participantRepository.findByCompetitionId(competitionId);
        Map<Long, String> names = namesFor(ps.stream().map(CompetitionParticipant::getUserId).collect(Collectors.toSet()));
        return ps.stream().map(p -> new CompetitionDto.ParticipantView(p.getId(), p.getUserId(),
                names.getOrDefault(p.getUserId(), "user" + p.getUserId()), p.getStatus(),
                p.getScore(), p.getUsedTime(), p.getStartedAt(), p.getFinishedAt())).toList();
    }

    // ==================== 限时赛参赛 ====================

    @Transactional
    public List<CompetitionDto.LobbyItem> lobby(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        // 惰性状态推进 + 到期结算（仅扫描可能变化的记录，规模可控）
        syncAllStatuses(now);
        List<Competition> comps = competitionRepository.findByStatusInOrderByIdDesc(
                List.of("published", "ongoing", "ended"));
        List<CompetitionDto.LobbyItem> items = new ArrayList<>();
        for (Competition c : comps) {
            CompetitionParticipant mine = participantRepository
                    .findByCompetitionIdAndUserId(c.getId(), userId).orElse(null);
            long count = participantRepository.countByCompetitionIdAndStatus(c.getId(), "finished")
                    + participantRepository.countByCompetitionIdAndStatus(c.getId(), "playing");
            Integer myRank = mine != null && !"joined".equals(mine.getStatus())
                    ? leaderboardService.rankOf(c.getId(), userId) : null;
            items.add(new CompetitionDto.LobbyItem(c.getId(), c.getTitle(), c.getCompetitionType(), c.getStatus(),
                    c.getStartTime(), c.getEndTime(), c.getDuration(), c.getDrawCount(), c.getTotalScore(),
                    c.getAllowPk(), count, mine != null, mine == null ? null : mine.getId(), myRank));
        }
        return items;
    }

    @Transactional
    public CompetitionDto.ParticipantView join(Long competitionId, Long userId) {
        Competition c = ensureActiveForJoin(competitionId);
        Optional<CompetitionParticipant> existing = participantRepository.findByCompetitionIdAndUserId(competitionId, userId);
        if (existing.isPresent()) {
            CompetitionParticipant p = existing.get();
            return toParticipantView(p);
        }
        CompetitionParticipant p = participantRepository.save(CompetitionParticipant.builder()
                .competitionId(competitionId).userId(userId).status("joined").usedTime(0).score(0.0).build());
        return toParticipantView(p);
    }

    @Transactional
    public CompetitionDto.StartResponse start(Long competitionId, Long userId) {
        Competition c = ensurePlaying(competitionId);
        CompetitionParticipant p = requireParticipant(competitionId, userId);
        if ("joined".equals(p.getStatus())) {
            List<CompetitionQuestion> pool = cqRepository.findByCompetitionIdOrderByOrderAsc(competitionId);
            if (pool.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "竞赛暂无题目");
            List<Long> assigned;
            int draw = c.getDrawCount() == null ? 0 : c.getDrawCount();
            if (draw <= 0 || draw >= pool.size()) {
                assigned = List.of(); // 全量模式
            } else {
                List<CompetitionQuestion> shuffled = new ArrayList<>(pool);
                Collections.shuffle(shuffled);
                assigned = shuffled.subList(0, draw).stream().map(CompetitionQuestion::getId).toList();
            }
            p.setStatus("playing");
            p.setAssignedCqIds(new ArrayList<>(assigned));
            p.setStartedAt(LocalDateTime.now());
            participantRepository.save(p);
        }
        List<CompetitionQuestion> questions = resolveQuestions(c, p);
        LocalDateTime deadline = deadlineOf(c, p);
        return new CompetitionDto.StartResponse(competitionId, p.getId(), deadline,
                questions.stream().map(this::toPlayQuestion).toList());
    }

    @Transactional
    public CompetitionDto.PlayStateResponse playState(Long competitionId, Long userId) {
        Competition c = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "竞赛不存在"));
        CompetitionParticipant p = requireParticipant(competitionId, userId);
        settleIfNeeded(c, p);
        List<CompetitionQuestion> questions = "joined".equals(p.getStatus())
                ? List.of() : resolveQuestions(c, p);
        List<CompetitionDto.AnsweredView> answers = answerRepository.findByParticipantId(p.getId()).stream()
                .map(a -> new CompetitionDto.AnsweredView(a.getCqId(), a.getAnswer(), a.getIsCorrect(),
                        a.getGainedScore(), a.getTimeSpent())).toList();
        LocalDateTime deadline = deadlineOf(c, p);
        int remaining = remainingSeconds(deadline);
        return new CompetitionDto.PlayStateResponse(p.getStatus(), p.getScore(),
                answers.size(), questions.size(), remaining, deadline,
                questions.stream().map(this::toPlayQuestion).toList(), answers);
    }

    @Transactional
    public CompetitionDto.AnswerResponse answer(Long competitionId, Long userId, CompetitionDto.AnswerRequest req) {
        Competition c = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "竞赛不存在"));
        CompetitionParticipant p = requireParticipant(competitionId, userId);
        settleIfNeeded(c, p);
        if ("finished".equals(p.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已交卷或超时自动交卷，不可再作答");
        }
        if (!"playing".equals(p.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请先开始答题");
        }
        CompetitionQuestion cq = cqRepository.findByIdAndCompetitionId(req.cqId(), competitionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "题目不属于本竞赛"));
        List<Long> assigned = p.getAssignedCqIds();
        if (assigned != null && !assigned.isEmpty() && !assigned.contains(cq.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该题不在你的抽题范围内");
        }
        if (answerRepository.existsByParticipantIdAndCqId(p.getId(), cq.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该题已作答，不能重复提交");
        }

        LocalDateTime now = LocalDateTime.now();
        List<CompetitionAnswer> prev = answerRepository.findByParticipantId(p.getId());
        LocalDateTime lastTime = prev.stream().map(CompetitionAnswer::getAnsweredAt)
                .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(p.getStartedAt());
        double perQuestionSec = perQuestionSeconds(c, p, cq);
        int timeSpent = computeTimeSpent(lastTime, now, perQuestionSec);

        String studentAnswer = req.answer() == null ? "" : req.answer().trim();
        boolean correct = !studentAnswer.isEmpty()
                && answerComparator.isCorrect(cq.getQType(), cq.getAnswer(), studentAnswer);
        double full = cq.getScore() == null ? 0 : cq.getScore();
        double gained = correct
                ? scoringService.scoreForAnswer(full, timeSpent, perQuestionSec,
                scoringService.normalizeRule(c.getScoringRule()))
                : 0.0;

        answerRepository.save(CompetitionAnswer.builder()
                .participantId(p.getId()).cqId(cq.getId()).answer(studentAnswer)
                .isCorrect(correct).gainedScore(gained).timeSpent(timeSpent).answeredAt(now).build());

        // 重新聚合得分（避免会话缓存重复计分）
        double totalScore = answerRepository.sumGainedScore(p.getId());
        p.setScore(ScoringService.round2(totalScore));
        participantRepository.save(p);

        pushService.leaderboardUpdate(competitionId, leaderboardService.top(competitionId, 10));
        return new CompetitionDto.AnswerResponse(cq.getId(), correct, gained, cq.getAnswer(), p.getScore());
    }

    @Transactional
    public CompetitionDto.FinishResponse finish(Long competitionId, Long userId) {
        Competition c = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "竞赛不存在"));
        CompetitionParticipant p = requireParticipant(competitionId, userId);
        settleIfNeeded(c, p);
        if ("joined".equals(p.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "尚未开始答题，无需交卷");
        }
        if (!"finished".equals(p.getStatus())) {
            finalizeParticipant(p, c);
        }
        CompetitionDto.LeaderboardResponse lb = leaderboardService.leaderboard(competitionId, userId);
        int total = lb.entries().size();
        pushService.leaderboardUpdate(competitionId, leaderboardService.top(competitionId, 10));
        return new CompetitionDto.FinishResponse(competitionId, p.getScore(), p.getUsedTime(),
                lb.myRank() == null ? 0 : lb.myRank(), total);
    }

    // ==================== 惰性状态推进 / 结算 ====================

    /** 单竞赛状态推进：published→ongoing→ended（过点结算积分） */
    private Competition syncOne(Competition c, LocalDateTime now) {
        if ("published".equals(c.getStatus()) && c.getStartTime() != null && !now.isBefore(c.getStartTime())) {
            if (c.getEndTime() != null && now.isBefore(c.getEndTime())) {
                c.setStatus("ongoing");
                competitionRepository.save(c);
            }
        }
        if (("published".equals(c.getStatus()) || "ongoing".equals(c.getStatus()))
                && c.getEndTime() != null && !now.isBefore(c.getEndTime())) {
            c.setStatus("ended");
            competitionRepository.save(c);
            settleCompetitionPoints(c.getId());
        }
        return c;
    }

    private void syncAllStatuses(LocalDateTime now) {
        List<Competition> candidates = competitionRepository.findByStatusInAndStartTimeBeforeAndEndTimeAfter(
                List.of("published"), now, now);
        for (Competition c : candidates) {
            if ("published".equals(c.getStatus()) && !now.isBefore(c.getStartTime())) {
                c.setStatus("ongoing");
                competitionRepository.save(c);
            }
        }
        // 到期结束：published/ongoing 且已过 endTime
        List<Competition> expiring = new ArrayList<>();
        expiring.addAll(competitionRepository.findByStatusAndEndTimeBefore("published", now));
        expiring.addAll(competitionRepository.findByStatusAndEndTimeBefore("ongoing", now));
        for (Competition c : expiring) {
            c.setStatus("ended");
            competitionRepository.save(c);
            settleCompetitionPoints(c.getId());
        }
    }

    /** 竞赛结束统一积分结算：CAS 幂等，按完赛名次发放限时赛积分 */
    @Transactional
    public void settleCompetitionPoints(Long competitionId) {
        if (competitionRepository.claimPointsSettlement(competitionId) != 1) {
            return; // 已被结算
        }
        Competition c = competitionRepository.findById(competitionId).orElse(null);
        if (c == null) return;
        List<CompetitionParticipant> ranked = participantRepository.findLeaderboard(competitionId).stream()
                .filter(p -> "finished".equals(p.getStatus())).toList();
        int total = ranked.size();
        double totalScore = c.getTotalScore() == null ? 0 : c.getTotalScore();
        for (int i = 0; i < ranked.size(); i++) {
            CompetitionParticipant p = ranked.get(i);
            int rank = i + 1;
            boolean perfect = totalScore > 0 && p.getScore() >= totalScore - 0.01;
            try {
                gamificationService.awardTimedPoints(p.getUserId(), competitionId, rank, total,
                        p.getScore(), totalScore, perfect);
            } catch (Exception e) {
                log.warn("award timed points failed user={} comp={} err={}", p.getUserId(), competitionId, e.getMessage());
            }
            try {
                notificationService.create(p.getUserId(), "竞赛结算",
                        "竞赛「" + c.getTitle() + "」已结算，你的名次第 " + rank + " 名，得分 " + p.getScore(),
                        com.exam.backend.domain.enums.NotificationTypeEnum.success, "competition", competitionId);
            } catch (Exception e) {
                log.warn("settle notification failed user={} comp={} err={}", p.getUserId(), competitionId, e.getMessage());
            }
        }
        log.info("competition {} points settled, finishers={}", competitionId, total);
    }

    /** 参赛者个人超时 / 竞赛窗口结束的惰性交卷 */
    private void settleIfNeeded(Competition c, CompetitionParticipant p) {
        if (!"playing".equals(p.getStatus())) return;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = deadlineOf(c, p);
        boolean expired = (deadline != null && !now.isBefore(deadline))
                || (c.getEndTime() != null && !now.isBefore(c.getEndTime()));
        if (expired) {
            finalizeParticipant(p, c);
        }
    }

    private void finalizeParticipant(CompetitionParticipant p, Competition c) {
        LocalDateTime now = LocalDateTime.now();
        int usedTime = p.getStartedAt() == null ? 0 : (int) Duration.between(p.getStartedAt(), now).getSeconds();
        double totalScore = answerRepository.sumGainedScore(p.getId());
        p.setScore(ScoringService.round2(totalScore));
        p.setUsedTime(usedTime);
        p.setStatus("finished");
        p.setFinishedAt(now);
        participantRepository.save(p);
    }

    // ==================== 内部工具 ====================

    private Competition ensureActiveForJoin(Long competitionId) {
        Competition c = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "竞赛不存在"));
        c = syncOne(c, LocalDateTime.now());
        if ("draft".equals(c.getStatus()) || "ended".equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前竞赛不可报名");
        }
        return c;
    }

    private Competition ensurePlaying(Long competitionId) {
        Competition c = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "竞赛不存在"));
        c = syncOne(c, LocalDateTime.now());
        if (!"ongoing".equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "竞赛不在进行中，无法答题");
        }
        return c;
    }

    private CompetitionParticipant requireParticipant(Long competitionId, Long userId) {
        return participantRepository.findByCompetitionIdAndUserId(competitionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "请先报名该竞赛"));
    }

    private Competition requireOwn(Long competitionId, Long teacherId) {
        return competitionRepository.findByIdAndCreatorId(competitionId, teacherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "只能操作自己创建的竞赛"));
    }

    /** 参赛者实际作答题目集合（assigned 为空 = 全量） */
    private List<CompetitionQuestion> resolveQuestions(Competition c, CompetitionParticipant p) {
        List<Long> assigned = p.getAssignedCqIds();
        List<CompetitionQuestion> all = cqRepository.findByCompetitionIdOrderByOrderAsc(c.getId());
        if (assigned == null || assigned.isEmpty()) return all;
        Set<Long> set = Set.copyOf(assigned);
        return all.stream().filter(q -> set.contains(q.getId())).toList();
    }

    private LocalDateTime deadlineOf(Competition c, CompetitionParticipant p) {
        if (p.getStartedAt() == null) return c.getEndTime();
        int minutes = c.getDuration() == null ? 15 : c.getDuration();
        LocalDateTime personal = p.getStartedAt().plusMinutes(minutes);
        if (c.getEndTime() == null) return personal;
        return personal.isBefore(c.getEndTime()) ? personal : c.getEndTime();
    }

    /** 单题均时（秒）= duration*60 / 题目数 */
    private double perQuestionSeconds(Competition c, CompetitionParticipant p, CompetitionQuestion cq) {
        int minutes = c.getDuration() == null ? 15 : c.getDuration();
        int count = resolveQuestions(c, p).size();
        if (count <= 0) return minutes * 60.0;
        return (minutes * 60.0) / count;
    }

    private int computeTimeSpent(LocalDateTime lastTime, LocalDateTime now, double perQuestionSec) {
        int raw = lastTime == null ? 0 : (int) Duration.between(lastTime, now).getSeconds();
        if (raw < 0) raw = 0;
        int cap = (int) Math.floor(perQuestionSec * 2);
        return cap > 0 ? Math.min(raw, cap) : raw;
    }

    private int remainingSeconds(LocalDateTime deadline) {
        if (deadline == null) return 0;
        long s = Duration.between(LocalDateTime.now(), deadline).getSeconds();
        return (int) Math.max(0, s);
    }

    private CompetitionDto.PlayQuestion toPlayQuestion(CompetitionQuestion q) {
        return new CompetitionDto.PlayQuestion(q.getId(), q.getQType(), q.getQContent(),
                q.getOptions(), q.getScore(), q.getOrder());
    }

    private CompetitionDto.ParticipantView toParticipantView(CompetitionParticipant p) {
        String name = userRepository.findById(p.getUserId()).map(User::getUsername).orElse("user" + p.getUserId());
        return new CompetitionDto.ParticipantView(p.getId(), p.getUserId(), name, p.getStatus(),
                p.getScore(), p.getUsedTime(), p.getStartedAt(), p.getFinishedAt());
    }

    private CompetitionDto.CompetitionView toView(Competition c) {
        long count = participantRepository.countByCompetitionIdAndStatus(c.getId(), "finished")
                + participantRepository.countByCompetitionIdAndStatus(c.getId(), "playing");
        return new CompetitionDto.CompetitionView(c.getId(), c.getTitle(), c.getDescription(),
                c.getCompetitionType(), c.getStatus(), c.getStartTime(), c.getEndTime(),
                c.getDuration(), c.getDrawCount(), c.getTotalScore(), c.getAllowPk(), c.getScoringRule(),
                c.getCreatorId(), count, c.getCreatedAt());
    }

    private Map<Long, String> namesFor(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids.isEmpty()) return map;
        for (User u : userRepository.findAllById(ids)) map.put(u.getId(), u.getUsername());
        return map;
    }
}
