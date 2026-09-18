package com.exam.backend.service.competition;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.User;
import com.exam.backend.domain.entity.competition.Competition;
import com.exam.backend.domain.entity.competition.CompetitionQuestion;
import com.exam.backend.domain.entity.competition.PkAnswer;
import com.exam.backend.domain.entity.competition.PkBattle;
import com.exam.backend.dto.CompetitionDto;
import com.exam.backend.repository.UserRepository;
import com.exam.backend.repository.competition.CompetitionQuestionRepository;
import com.exam.backend.repository.competition.CompetitionRepository;
import com.exam.backend.repository.competition.PkAnswerRepository;
import com.exam.backend.repository.competition.PkBattleRepository;
import com.exam.backend.util.AnswerComparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 1v1 PK 对战服务 (v4.0)：以进行中且 allowPk 的竞赛全量题库为题源，双方同题。
 * waiting 30 分钟未接受自动 cancelled；结算用 CAS 抢权；分高者胜/同分先完成者胜/都无完成时间平局。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PkService {

    private static final int WAITING_TTL_MINUTES = 30;

    private final PkBattleRepository battleRepository;
    private final PkAnswerRepository pkAnswerRepository;
    private final CompetitionRepository competitionRepository;
    private final CompetitionQuestionRepository cqRepository;
    private final UserRepository userRepository;
    private final ScoringService scoringService;
    private final GamificationService gamificationService;
    private final AnswerComparator answerComparator;
    private final RealtimePushService pushService;

    // ==================== 发起 / 大厅 ====================

    @Transactional
    public CompetitionDto.PkBattleView create(Long competitionId, Long challengerId, CompetitionDto.PkCreateRequest req) {
        Competition c = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "竞赛不存在"));
        if (!Boolean.TRUE.equals(c.getAllowPk())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该竞赛未开启 PK");
        }
        if (!"ongoing".equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅进行中的竞赛可发起 PK");
        }
        if (req != null && req.opponentId() != null && req.opponentId().equals(challengerId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "不能挑战自己");
        }
        if (battleRepository.existsByCompetitionIdAndChallengerIdAndStatus(competitionId, challengerId, "waiting")) {
            throw new BusinessException(ErrorCode.CONFLICT, "你已有一个等待中的挑战");
        }
        PkBattle b = battleRepository.save(PkBattle.builder()
                .competitionId(competitionId).challengerId(challengerId)
                .opponentId(req == null ? null : req.opponentId())
                .status("waiting").build());
        return toView(b);
    }

    @Transactional
    public CompetitionDto.PkLobbyResponse lobby(Long userId) {
        expireStaleWaiting();
        List<PkBattle> waiting = battleRepository.findByStatusOrderByIdDesc("waiting").stream()
                .filter(b -> !b.getChallengerId().equals(userId))
                .filter(b -> competitionRepository.findById(b.getCompetitionId())
                        .map(c -> Boolean.TRUE.equals(c.getAllowPk()) && "ongoing".equals(c.getStatus())).orElse(false))
                .toList();
        List<PkBattle> mine = battleRepository.findAll().stream()
                .filter(b -> userId.equals(b.getChallengerId()) || userId.equals(b.getOpponentId()))
                .filter(b -> !(b.getStartedAt() == null && ("cancelled".equals(b.getStatus()) || "finished".equals(b.getStatus()))))
                .sorted((x, y) -> Long.compare(y.getId(), x.getId()))
                .toList();
        return new CompetitionDto.PkLobbyResponse(waiting.stream().map(this::toView).toList(),
                mine.stream().map(this::toView).toList());
    }

    // ==================== 接受 / 状态 ====================

    @Transactional
    public CompetitionDto.PkStateResponse accept(Long battleId, Long userId) {
        expireStaleWaiting();
        PkBattle b = requireBattle(battleId);
        if (b.getChallengerId().equals(userId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "不能接受自己发起的挑战");
        }
        if ("waiting".equals(b.getStatus())) {
            if (b.getOpponentId() != null && !b.getOpponentId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "该挑战指定了其他对手");
            }
            b.setStatus("playing");
            b.setOpponentId(userId);
            b.setStartedAt(LocalDateTime.now());
            battleRepository.save(b);
            pushService.battleUpdate(battleId, Map.of("event", "battle_start", "data", toView(b)));
        } else if (!"playing".equals(b.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "对战已开始或已结束");
        }
        return state(battleId, userId);
    }

    @Transactional
    public CompetitionDto.PkStateResponse state(Long battleId, Long userId) {
        PkBattle b = requireBattle(battleId);
        requireParticipant(b, userId);
        settleIfNeeded(b);
        Competition c = competitionRepository.findById(b.getCompetitionId()).orElse(null);
        List<CompetitionQuestion> questions = c == null ? List.of()
                : cqRepository.findByCompetitionIdOrderByOrderAsc(c.getId());
        boolean isChallenger = userId.equals(b.getChallengerId());
        LocalDateTime myFinished = isChallenger ? b.getChallengerFinishedAt() : b.getOpponentFinishedAt();
        List<CompetitionDto.AnsweredView> myAnswers = pkAnswerRepository.findByBattleIdAndPlayerId(battleId, userId).stream()
                .map(a -> new CompetitionDto.AnsweredView(a.getCqId(), a.getAnswer(), a.getIsCorrect(),
                        a.getGainedScore(), a.getTimeSpent())).toList();
        LocalDateTime deadline = deadlineOf(c, b);
        String myStatus = myFinished != null ? "finished" : b.getStatus();
        return new CompetitionDto.PkStateResponse(toView(b), isChallenger ? "challenger" : "opponent",
                myStatus, questions.stream().map(this::toPlayQuestion).toList(), myAnswers,
                remainingSeconds(deadline), deadline);
    }

    // ==================== 作答 / 交卷 ====================

    @Transactional
    public CompetitionDto.PkAnswerResponse answer(Long battleId, Long userId, CompetitionDto.PkAnswerRequest req) {
        PkBattle b = requireBattle(battleId);
        requireParticipant(b, userId);
        settleIfNeeded(b);
        if (!"playing".equals(b.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "对战已结束");
        }
        Competition c = competitionRepository.findById(b.getCompetitionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "竞赛不存在"));
        CompetitionQuestion cq = cqRepository.findByIdAndCompetitionId(req.cqId(), c.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "题目不属于本对战"));
        if (pkAnswerRepository.existsByBattleIdAndPlayerIdAndCqId(battleId, userId, cq.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该题已作答");
        }
        LocalDateTime now = LocalDateTime.now();
        List<PkAnswer> prev = pkAnswerRepository.findByBattleIdAndPlayerId(battleId, userId);
        LocalDateTime lastTime = prev.stream().map(PkAnswer::getAnsweredAt)
                .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(b.getStartedAt());
        int minutes = c.getDuration() == null ? 15 : c.getDuration();
        int count = Math.max(1, cqRepository.countByCompetitionId(c.getId()) > 0
                ? (int) cqRepository.countByCompetitionId(c.getId()) : 1);
        double perQuestionSec = (minutes * 60.0) / count;
        int raw = lastTime == null ? 0 : (int) Duration.between(lastTime, now).getSeconds();
        if (raw < 0) raw = 0;
        int timeSpent = (int) Math.min(raw, Math.floor(perQuestionSec * 2));

        String studentAnswer = req.answer() == null ? "" : req.answer().trim();
        boolean correct = !studentAnswer.isEmpty()
                && answerComparator.isCorrect(cq.getQType(), cq.getAnswer(), studentAnswer);
        double full = cq.getScore() == null ? 0 : cq.getScore();
        double gained = correct ? scoringService.scoreForAnswer(full, timeSpent, perQuestionSec,
                scoringService.normalizeRule(c.getScoringRule())) : 0.0;

        pkAnswerRepository.save(PkAnswer.builder()
                .battleId(battleId).playerId(userId).cqId(cq.getId()).answer(studentAnswer)
                .isCorrect(correct).gainedScore(gained).timeSpent(timeSpent).answeredAt(now).build());

        refreshProgress(b, userId);
        battleRepository.save(b);
        pushService.battleUpdate(battleId, Map.of("event", "progress", "data", toView(b)));
        return new CompetitionDto.PkAnswerResponse(cq.getId(), correct, gained, cq.getAnswer(),
                b.getChallengerId().equals(userId) ? b.getChallengerScore() : b.getOpponentScore(),
                b.getChallengerId().equals(userId) ? b.getOpponentScore() : b.getChallengerScore());
    }

    @Transactional
    public CompetitionDto.PkStateResponse finish(Long battleId, Long userId) {
        PkBattle b = requireBattle(battleId);
        requireParticipant(b, userId);
        settleIfNeeded(b);
        if ("playing".equals(b.getStatus())) {
            LocalDateTime now = LocalDateTime.now();
            if (userId.equals(b.getChallengerId())) b.setChallengerFinishedAt(now);
            else b.setOpponentFinishedAt(now);
            battleRepository.save(b);
            if (b.getChallengerFinishedAt() != null && b.getOpponentFinishedAt() != null) {
                settleBattle(battleId);
            }
        }
        return state(battleId, userId);
    }

    // ==================== 结算 ====================

    /** 惰性结算：双方完成或超时触发；CAS 抢权保证并发只结算一次 */
    private void settleIfNeeded(PkBattle b) {
        if (!"playing".equals(b.getStatus())) return;
        boolean bothDone = b.getChallengerFinishedAt() != null && b.getOpponentFinishedAt() != null;
        Competition c = competitionRepository.findById(b.getCompetitionId()).orElse(null);
        LocalDateTime deadline = deadlineOf(c, b);
        boolean timeout = deadline != null && !LocalDateTime.now().isBefore(deadline);
        if (bothDone || timeout) {
            settleBattle(b.getId());
        }
    }

    @Transactional
    public void settleBattle(Long battleId) {
        if (battleRepository.claimSettlement(battleId) != 1) {
            return; // 结算权已被抢占
        }
        PkBattle b = requireBattle(battleId);
        // 用 SUM 重算双方得分，避免进度字段滞后
        double cs = pkAnswerRepository.sumGainedScore(battleId, b.getChallengerId());
        double os = pkAnswerRepository.sumGainedScore(battleId, b.getOpponentId() == null ? -1L : b.getOpponentId());
        b.setChallengerScore(ScoringService.round2(cs));
        b.setOpponentScore(ScoringService.round2(os));

        Long winnerId = decideWinner(b, cs, os);
        b.setWinnerId(winnerId);
        b.setFinishedAt(LocalDateTime.now());
        battleRepository.save(b);

        if (b.getOpponentId() != null) {
            try {
                gamificationService.applyPkResult(battleId, b.getChallengerId(), b.getOpponentId(), winnerId);
            } catch (Exception e) {
                log.warn("pk award failed battle={} err={}", battleId, e.getMessage());
            }
        }
        pushService.battleUpdate(battleId, Map.of("event", "battle_end", "data", toView(b)));
    }

    private Long decideWinner(PkBattle b, double cs, double os) {
        if (Double.compare(cs, os) > 0) return b.getChallengerId();
        if (Double.compare(os, cs) > 0) return b.getOpponentId();
        // 同分：先完成者胜
        LocalDateTime cf = b.getChallengerFinishedAt();
        LocalDateTime of = b.getOpponentFinishedAt();
        if (cf != null && of == null) return b.getChallengerId();
        if (of != null && cf == null) return b.getOpponentId();
        if (cf != null && of != null) return cf.isBefore(of) ? b.getChallengerId() : b.getOpponentId();
        return null; // 都无完成时间 → 平局
    }

    private void refreshProgress(PkBattle b, Long userId) {
        double score = pkAnswerRepository.sumGainedScore(b.getId(), userId);
        long answered = pkAnswerRepository.countAnswered(b.getId(), userId);
        if (userId.equals(b.getChallengerId())) {
            b.setChallengerScore(ScoringService.round2(score));
            b.setChallengerAnswered((int) answered);
        } else {
            b.setOpponentScore(ScoringService.round2(score));
            b.setOpponentAnswered((int) answered);
        }
    }

    private void expireStaleWaiting() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(WAITING_TTL_MINUTES);
        for (PkBattle b : battleRepository.findByStatusAndCreatedAtBefore("waiting", before)) {
            battleRepository.claimCancel(b.getId());
        }
    }

    // ==================== 内部工具 ====================

    private PkBattle requireBattle(Long battleId) {
        return battleRepository.findById(battleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "对战不存在"));
    }

    private void requireParticipant(PkBattle b, Long userId) {
        if (!userId.equals(b.getChallengerId()) && !userId.equals(b.getOpponentId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "非对战双方不可访问");
        }
    }

    private LocalDateTime deadlineOf(Competition c, PkBattle b) {
        if (b.getStartedAt() == null) return null;
        int minutes = c == null || c.getDuration() == null ? 15 : c.getDuration();
        return b.getStartedAt().plusMinutes(minutes);
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

    private CompetitionDto.PkBattleView toView(PkBattle b) {
        Map<Long, String> names = namesFor(Stream.of(b.getChallengerId(), b.getOpponentId())
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        String title = competitionRepository.findById(b.getCompetitionId())
                .map(Competition::getTitle).orElse(null);
        return new CompetitionDto.PkBattleView(b.getId(), b.getCompetitionId(), title,
                b.getChallengerId(), names.get(b.getChallengerId()),
                b.getOpponentId(), b.getOpponentId() == null ? null : names.get(b.getOpponentId()),
                b.getStatus(), b.getWinnerId(), b.getChallengerScore(), b.getOpponentScore(),
                b.getChallengerAnswered(), b.getOpponentAnswered(),
                b.getStartedAt(), b.getFinishedAt(), b.getCreatedAt());
    }

    private Map<Long, String> namesFor(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids.isEmpty()) return map;
        for (User u : userRepository.findAllById(ids)) map.put(u.getId(), u.getUsername());
        return map;
    }
}
