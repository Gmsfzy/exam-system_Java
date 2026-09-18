package com.exam.backend.service.bounty;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.Major;
import com.exam.backend.domain.entity.Question;
import com.exam.backend.domain.entity.User;
import com.exam.backend.domain.entity.bounty.Bounty;
import com.exam.backend.domain.entity.bounty.BountySubmission;
import com.exam.backend.domain.enums.DifficultyEnum;
import com.exam.backend.domain.enums.QuestionTypeEnum;
import com.exam.backend.dto.BountyDto;
import com.exam.backend.repository.MajorRepository;
import com.exam.backend.repository.QuestionRepository;
import com.exam.backend.repository.UserRepository;
import com.exam.backend.repository.bounty.BountyRepository;
import com.exam.backend.repository.bounty.BountySubmissionRepository;
import com.exam.backend.service.NotificationService;
import com.exam.backend.domain.enums.NotificationTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 征集悬赏服务 (v4.0)，对齐文档 4.14：发布 / 广场 / 详情 / 投稿 / 采纳闭环 / 拒绝。
 * <p>师生同权；采纳用 CAS 抢锁（open→closed），题目投稿入库 Question(source=bounty)、
 * 答案投稿回填原题 analysis；采纳提交后在独立事务加积分+勋章，失败不回滚采纳。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BountyService {

    private final BountyRepository bountyRepository;
    private final BountySubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final MajorRepository majorRepository;
    private final QuestionRepository questionRepository;
    private final BountyRewardService rewardService;
    private final NotificationService notificationService;

    // ==================== 查询 ====================

    /** 悬赏广场：type / status 可选筛选；快照 answer 仅发布者可见（v3.5） */
    @Transactional(readOnly = true)
    public List<BountyDto.BountyListItem> list(String type, String status, Long viewerId) {
        List<Bounty> all = bountyRepository.findAllByOrderByIdDesc();
        NameMaps maps = loadNames(all.stream().map(Bounty::getPublisherId).collect(Collectors.toSet()),
                all.stream().map(Bounty::getMajorId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<BountyDto.BountyListItem> out = new ArrayList<>();
        for (Bounty b : all) {
            String eff = effectiveStatus(b);
            if (type != null && !type.isBlank() && !type.equals(b.getBountyType())) continue;
            if (status != null && !status.isBlank() && !status.equals(eff)) continue;
            out.add(toListItem(b, eff, viewerId, maps));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<BountyDto.BountyListItem> mine(Long viewerId) {
        List<Bounty> all = bountyRepository.findByPublisherIdOrderByIdDesc(viewerId);
        NameMaps maps = loadNames(Set.of(viewerId),
                all.stream().map(Bounty::getMajorId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<BountyDto.BountyListItem> out = new ArrayList<>();
        for (Bounty b : all) out.add(toListItem(b, effectiveStatus(b), viewerId, maps));
        return out;
    }

    @Transactional(readOnly = true)
    public List<BountyDto.MySubmissionItem> mySubmissions(Long viewerId) {
        List<BountySubmission> subs = submissionRepository.findBySubmitterIdOrderByIdDesc(viewerId);
        if (subs.isEmpty()) return List.of();
        Map<Long, Bounty> bountyMap = bountyRepository.findAllById(
                        subs.stream().map(BountySubmission::getBountyId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Bounty::getId, b -> b));
        List<BountyDto.MySubmissionItem> out = new ArrayList<>();
        for (BountySubmission s : subs) {
            Bounty b = bountyMap.get(s.getBountyId());
            if (b == null) continue;
            out.add(new BountyDto.MySubmissionItem(s.getId(), b.getId(), b.getTitle(), b.getBountyType(),
                    b.getRewardPoints(), s.getStatus(), effectiveStatus(b), s.getCreatedAt(), s.getReviewedAt()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public BountyDto.BountyDetailView detail(Long id, Long viewerId, boolean isTeacher) {
        Bounty b = bountyRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "悬赏不存在"));
        String eff = effectiveStatus(b);
        NameMaps maps = loadNames(Set.of(b.getPublisherId()),
                b.getMajorId() == null ? Set.of() : Set.of(b.getMajorId()));
        boolean isPublisher = Objects.equals(b.getPublisherId(), viewerId);
        BountySubmission mine = viewerId == null ? null
                : submissionRepository.findByBountyIdAndSubmitterId(id, viewerId).orElse(null);
        return new BountyDto.BountyDetailView(
                b.getId(), b.getBountyType(), b.getTitle(), b.getDescription(),
                b.getPublisherId(), maps.userName(b.getPublisherId()), b.getMajorId(), maps.majorName(b.getMajorId()),
                b.getQType(), b.getQDifficulty(), b.getRewardPoints(),
                eff, b.getDeadline(), b.getCreatedAt(), b.getClosedAt(),
                (int) submissionRepository.countByBountyId(id),
                (int) submissionRepository.countByBountyIdAndStatus(id, "pending"),
                resolveTargetQuestion(b, isTeacher), sanitizeSnapshot(b.getTargetQuestionSnapshot(), isPublisher),
                isPublisher, b.getAcceptedSubmissionId(), toSubmissionView(mine, nameOf(mine == null ? null : mine.getSubmitterId())));
    }

    /** 投稿列表：发布者看全部，其他人只看已采纳 */
    @Transactional(readOnly = true)
    public List<BountyDto.SubmissionView> submissions(Long id, Long viewerId) {
        Bounty b = bountyRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "悬赏不存在"));
        boolean isPublisher = Objects.equals(b.getPublisherId(), viewerId);
        List<BountySubmission> subs = isPublisher
                ? submissionRepository.findByBountyIdOrderByIdDesc(id)
                : submissionRepository.findByBountyIdAndStatusOrderByIdDesc(id, "accepted");
        Map<Long, String> names = userNames(subs.stream().map(BountySubmission::getSubmitterId).collect(Collectors.toSet()));
        return subs.stream().map(s -> toSubmissionView(s, names.get(s.getSubmitterId()))).toList();
    }

    // ==================== 发布 / 投稿 ====================

    @Transactional
    public BountyDto.BountyDetailView publish(BountyDto.BountyRequest req, Long publisherId) {
        String type = (req.bountyType() == null || req.bountyType().isBlank()) ? "question" : req.bountyType().trim();
        if (!"question".equals(type) && !"answer".equals(type)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "悬赏类型仅支持 question / answer");
        }
        int reward = req.rewardPoints() == null ? 10 : req.rewardPoints();
        if (reward <= 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "赏金须为正整数");

        Bounty b = Bounty.builder()
                .publisherId(publisherId)
                .bountyType(type)
                .title(req.title().trim())
                .description(req.description())
                .rewardPoints(reward)
                .deadline(req.deadline())
                .status("open")
                .build();

        if ("question".equals(type)) {
            if (req.majorId() == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "题目征集必须选择专业");
            majorRepository.findById(req.majorId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "专业不存在"));
            b.setMajorId(req.majorId());
            b.setQType(req.qType());
            b.setQDifficulty(req.qDifficulty());
        } else {
            boolean hasTarget = req.targetQuestionId() != null;
            boolean hasSnapshot = req.targetQuestionSnapshot() != null && !req.targetQuestionSnapshot().isEmpty();
            if (!hasTarget && !hasSnapshot) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "答案征集需关联题库题或自带题目快照");
            }
            if (hasTarget) {
                questionRepository.findById(req.targetQuestionId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "关联题目不存在"));
                b.setTargetQuestionId(req.targetQuestionId());
            } else {
                b.setTargetQuestionSnapshot(new LinkedHashMap<>(req.targetQuestionSnapshot()));
            }
        }
        Bounty saved = bountyRepository.save(b);
        return detail(saved.getId(), publisherId, false);
    }

    @Transactional
    public BountyDto.SubmissionView submit(Long bountyId, BountyDto.SubmissionRequest req, Long submitterId) {
        Bounty b = bountyRepository.findById(bountyId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "悬赏不存在"));
        if (!"open".equals(effectiveStatus(b))) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "悬赏已关闭或已过期，不可投稿");
        if (Objects.equals(b.getPublisherId(), submitterId)) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "不能给自己发布的悬赏投稿");
        if (submissionRepository.existsByBountyIdAndSubmitterId(bountyId, submitterId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "每人每悬赏限投一条");
        }

        BountySubmission.BountySubmissionBuilder builder = BountySubmission.builder()
                .bountyId(bountyId).submitterId(submitterId).status("pending");
        if ("question".equals(b.getBountyType())) {
            if (isBlank(req.qContent()) || isBlank(req.qAnswer())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "题目投稿必须填写题干与答案");
            }
            builder.qContent(req.qContent().trim())
                    .qAnswer(req.qAnswer())
                    .qOptions(req.qOptions())
                    .qAnalysis(req.qAnalysis())
                    .qKnowledge(req.qKnowledge())
                    .qType(firstNonBlank(req.qType(), b.getQType(), "single_choice"))
                    .qDifficulty(firstNonBlank(req.qDifficulty(), b.getQDifficulty(), "medium"));
        } else {
            if (isBlank(req.content())) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "答案投稿正文不能为空");
            builder.content(req.content().trim());
        }
        try {
            BountySubmission saved = submissionRepository.save(builder.build());
            return toSubmissionView(saved, nameOf(submitterId));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "每人每悬赏限投一条");
        }
    }

    // ==================== 采纳 / 拒绝 ====================

    /**
     * 采纳（核心闭环）。仅发布者；CAS 抢锁 open→closed；题目入库/答案回填；其余 pending 批量拒绝。
     * 采纳事务提交后由调用方触发 {@link #settleReward} 独立发奖。
     */
    @Transactional
    public BountyDto.AcceptOutcome accept(Long sid, Long userId) {
        BountySubmission sub = submissionRepository.findById(sid)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "投稿不存在"));
        Bounty b = bountyRepository.findById(sub.getBountyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "悬赏不存在"));
        if (!Objects.equals(b.getPublisherId(), userId)) throw new BusinessException(ErrorCode.FORBIDDEN, "仅发布者可采纳投稿");
        if (!"pending".equals(sub.getStatus())) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该投稿已处理");
        if (!"open".equals(effectiveStatus(b))) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "悬赏已关闭或已过期");

        // 采纳所需字段先快照（claimClose 的 clearAutomatically 会清空持久化上下文）
        final Long bountyId = b.getId();
        final String bountyType = b.getBountyType();
        final Long majorId = b.getMajorId();
        final Long publisherId = b.getPublisherId();
        final Integer rewardPoints = b.getRewardPoints();
        final Long targetQuestionId = b.getTargetQuestionId();
        final String bqType = b.getQType();
        final String bDifficulty = b.getQDifficulty();
        final Long submitterId = sub.getSubmitterId();
        final String sContent = sub.getContent();
        final String sQContent = sub.getQContent();
        final List<String> sOptions = sub.getQOptions();
        final String sAnswer = sub.getQAnswer();
        final String sAnalysis = sub.getQAnalysis();
        final String sKnowledge = sub.getQKnowledge();
        final String sQType = sub.getQType();
        final String sDifficulty = sub.getQDifficulty();

        LocalDateTime now = LocalDateTime.now();
        int rows = bountyRepository.claimClose(bountyId, sid, now);
        if (rows != 1) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "悬赏已被处理，请刷新");

        Long acceptedQuestionId = null;
        if ("question".equals(bountyType)) {
            Question q = Question.builder()
                    .content(sQContent)
                    .options(sOptions)
                    .answer(sAnswer)
                    .analysis(sAnalysis)
                    .knowledge(sKnowledge)
                    .type(resolveType(firstNonBlank(sQType, bqType)))
                    .difficulty(resolveDifficulty(firstNonBlank(sDifficulty, bDifficulty)))
                    .source("bounty")
                    .creatorId(publisherId)
                    .isPublic(true)
                    .majorId(majorId)
                    .build();
            acceptedQuestionId = questionRepository.save(q).getId();
        } else if (targetQuestionId != null) {
            questionRepository.findById(targetQuestionId).ifPresent(tq -> {
                if (isBlank(tq.getAnalysis()) && !isBlank(sContent)) {
                    tq.setAnalysis(sContent);
                    questionRepository.save(tq);
                }
            });
        }

        BountySubmission fresh = submissionRepository.findById(sid).orElseThrow();
        fresh.setStatus("accepted");
        fresh.setReviewerId(userId);
        fresh.setReviewedAt(now);
        fresh.setAcceptedQuestionId(acceptedQuestionId);
        submissionRepository.save(fresh);

        submissionRepository.rejectRemaining(bountyId, sid, "悬赏已采纳其他投稿", now);

        return new BountyDto.AcceptOutcome(submitterId, rewardPoints, bountyId, acceptedQuestionId);
    }

    /** 采纳提交后独立发奖：失败仅记日志，不影响采纳结果 */
    public void settleReward(BountyDto.AcceptOutcome outcome) {
        try {
            int granted = rewardService.grant(outcome.submitterId(),
                    outcome.rewardPoints() == null ? 0 : outcome.rewardPoints(),
                    outcome.bountyId());
            notificationService.create(outcome.submitterId(), "悬赏采纳",
                    "你在悬赏 #" + outcome.bountyId() + " 的投稿已被采纳，获得 " + granted + " 竞技积分",
                    NotificationTypeEnum.success, "bounty", outcome.bountyId());
        } catch (Exception e) {
            log.error("bounty reward settle failed submitter={} bounty={}: {}",
                    outcome.submitterId(), outcome.bountyId(), e.getMessage(), e);
        }
    }

    @Transactional
    public void reject(Long sid, String comment, Long userId) {
        BountySubmission sub = submissionRepository.findById(sid)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "投稿不存在"));
        Bounty b = bountyRepository.findById(sub.getBountyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "悬赏不存在"));
        if (!Objects.equals(b.getPublisherId(), userId)) throw new BusinessException(ErrorCode.FORBIDDEN, "仅发布者可审核投稿");
        if (!"pending".equals(sub.getStatus())) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该投稿已处理");
        if (!"open".equals(effectiveStatus(b))) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "悬赏已关闭或已过期");
        sub.setStatus("rejected");
        sub.setReviewComment(comment);
        sub.setReviewerId(userId);
        sub.setReviewedAt(LocalDateTime.now());
        submissionRepository.save(sub);
    }

    // ==================== 辅助 ====================

    /** open 且已过 deadline 视为 expired（不落库） */
    private String effectiveStatus(Bounty b) {
        if (!"open".equals(b.getStatus())) return b.getStatus();
        if (b.getDeadline() != null && b.getDeadline().isBefore(LocalDateTime.now())) return "expired";
        return "open";
    }

    private BountyDto.BountyListItem toListItem(Bounty b, String eff, Long viewerId, NameMaps maps) {
        boolean isPublisher = Objects.equals(b.getPublisherId(), viewerId);
        return new BountyDto.BountyListItem(
                b.getId(), b.getBountyType(), b.getTitle(), b.getDescription(),
                b.getPublisherId(), maps.userName(b.getPublisherId()), b.getMajorId(), maps.majorName(b.getMajorId()),
                b.getQType(), b.getQDifficulty(), b.getRewardPoints(),
                eff, b.getDeadline(), b.getCreatedAt(),
                (int) submissionRepository.countByBountyId(b.getId()),
                (int) submissionRepository.countByBountyIdAndStatus(b.getId(), "pending"),
                sanitizeSnapshot(b.getTargetQuestionSnapshot(), isPublisher), isPublisher);
    }

    private BountyDto.SubmissionView toSubmissionView(BountySubmission s, String submitterName) {
        if (s == null) return null;
        return new BountyDto.SubmissionView(
                s.getId(), s.getBountyId(), s.getSubmitterId(), submitterName,
                s.getContent(), s.getQContent(), s.getQOptions(), s.getQAnswer(), s.getQAnalysis(),
                s.getQType(), s.getQDifficulty(), s.getQKnowledge(),
                s.getStatus(), s.getReviewComment(), s.getCreatedAt(), s.getReviewedAt());
    }

    /** 答案征集关联题库题：answer/analysis 仅教师下发（v3.5） */
    private BountyDto.TargetQuestionView resolveTargetQuestion(Bounty b, boolean isTeacher) {
        if (!"answer".equals(b.getBountyType()) || b.getTargetQuestionId() == null) return null;
        return questionRepository.findById(b.getTargetQuestionId()).map(q -> new BountyDto.TargetQuestionView(
                q.getId(), q.getContent(), q.getOptions(),
                isTeacher ? q.getAnswer() : null,
                isTeacher ? q.getAnalysis() : null)).orElse(null);
    }

    /** 快照 answer 仅发布者本人可见（v3.5） */
    private Map<String, Object> sanitizeSnapshot(Map<String, Object> snapshot, boolean isPublisher) {
        if (snapshot == null || snapshot.isEmpty()) return snapshot;
        if (isPublisher) return snapshot;
        Map<String, Object> copy = new LinkedHashMap<>(snapshot);
        copy.remove("answer");
        return copy;
    }

    private QuestionTypeEnum resolveType(String v) {
        QuestionTypeEnum t = QuestionTypeEnum.fromValue(v);
        return t == null ? QuestionTypeEnum.single_choice : t;
    }

    private DifficultyEnum resolveDifficulty(String v) {
        if (v != null) {
            try {
                return DifficultyEnum.valueOf(v.trim().toLowerCase());
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return DifficultyEnum.medium;
    }

    private String nameOf(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(User::getUsername).orElse("user" + userId);
    }

    private Map<Long, String> userNames(Set<Long> ids) {
        Map<Long, String> m = new HashMap<>();
        if (ids == null || ids.isEmpty()) return m;
        for (User u : userRepository.findAllById(ids)) m.put(u.getId(), u.getUsername());
        return m;
    }

    private NameMaps loadNames(Set<Long> userIds, Set<Long> majorIds) {
        Map<Long, String> users = userNames(userIds);
        Map<Long, String> majors = new HashMap<>();
        if (majorIds != null && !majorIds.isEmpty()) {
            for (Major mj : majorRepository.findAllById(majorIds)) majors.put(mj.getId(), mj.getName());
        }
        return new NameMaps(users, majors);
    }

    private record NameMaps(Map<Long, String> users, Map<Long, String> majors) {
        String userName(Long id) { return id == null ? null : users.getOrDefault(id, "user" + id); }
        String majorName(Long id) { return id == null ? null : majors.get(id); }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String firstNonBlank(String... vs) {
        for (String v : vs) if (!isBlank(v)) return v;
        return null;
    }
}
