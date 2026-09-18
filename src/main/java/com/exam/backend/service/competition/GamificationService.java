package com.exam.backend.service.competition;

import com.exam.backend.domain.entity.competition.UserBadge;
import com.exam.backend.domain.entity.competition.UserPointsProfile;
import com.exam.backend.repository.competition.UserBadgeRepository;
import com.exam.backend.repository.competition.UserPointsProfileRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 竞技化积分引擎 (v4.0)：段位实时推导、积分增减、勋章幂等发放、PK/限时赛计分。
 * <p>段位阈值：青铜0/白银100/黄金250/铂金450/钻石700/王者1000。
 * PK：胜 25+连胜×2(上限+10)/平 10/负 5(连胜清零)。
 * 限时赛：参与5 + 表现(得分率×30,上限30) + 名次分(1名40/2名30/3名20/4-10名10,单人不发) + 满分加成10。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GamificationService {

    // ---- 段位表 ----
    public record Tier(String name, String icon, int min) {}
    private static final Tier[] TIERS = {
            new Tier("青铜", "🥉", 0),
            new Tier("白银", "🥈", 100),
            new Tier("黄金", "🥇", 250),
            new Tier("铂金", "💠", 450),
            new Tier("钻石", "💎", 700),
            new Tier("王者", "👑", 1000),
    };

    // ---- 勋章目录 ----
    public static final String BADGE_FIRST_BLOOD = "first_blood";
    public static final String BADGE_STREAK3 = "streak_3";
    public static final String BADGE_STREAK5 = "streak_5";
    public static final String BADGE_VETERAN = "veteran";
    public static final String BADGE_PERFECT = "perfect";
    public static final String BADGE_SEASON_TOP = "season_top";
    public static final String BADGE_TEAM_FOUNDER = "team_founder";
    public static final String BADGE_BOUNTY_STAR = "bounty_star";

    private static final Map<String, String[]> BADGE_CATALOG = new LinkedHashMap<>();
    static {
        BADGE_CATALOG.put(BADGE_FIRST_BLOOD, new String[]{"首战告捷", "🥇", "完成第一场比赛"});
        BADGE_CATALOG.put(BADGE_STREAK3, new String[]{"三连胜", "🔥", "PK 达成三连胜"});
        BADGE_CATALOG.put(BADGE_STREAK5, new String[]{"五连王者", "👑", "PK 达成五连胜"});
        BADGE_CATALOG.put(BADGE_VETERAN, new String[]{"身经百战", "🎖️", "限时赛完赛满 10 场"});
        BADGE_CATALOG.put(BADGE_PERFECT, new String[]{"一战封神", "💯", "单场比赛获得满分"});
        BADGE_CATALOG.put(BADGE_SEASON_TOP, new String[]{"季度巅峰", "🏆", "赛季榜进入前三"});
        BADGE_CATALOG.put(BADGE_TEAM_FOUNDER, new String[]{"开疆辟土", "🛡️", "创建一支战队"});
        BADGE_CATALOG.put(BADGE_BOUNTY_STAR, new String[]{"悬赏新星", "🪙", "悬赏投稿首次被采纳"});
    }

    private final UserPointsProfileRepository profileRepository;
    private final UserBadgeRepository badgeRepository;
    private final RealtimePushService pushService;

    public static Map<String, String[]> badgeCatalog() {
        return BADGE_CATALOG;
    }

    public String currentSeason() {
        return YearMonth.now(ZoneId.of("Asia/Shanghai")).toString(); // yyyy-MM
    }

    public Tier tierOf(int points) {
        Tier current = TIERS[0];
        for (Tier t : TIERS) {
            if (points >= t.min()) current = t;
            else break;
        }
        return current;
    }

    /** 下一段位（已达最高返回 null） */
    public Tier nextTierOf(int points) {
        for (Tier t : TIERS) {
            if (points < t.min()) return t;
        }
        return null;
    }

    @Transactional
    public UserPointsProfile ensureProfile(Long userId, String season) {
        return profileRepository.findByUserIdAndSeason(userId, season).orElseGet(() ->
                profileRepository.save(UserPointsProfile.builder()
                        .userId(userId).season(season).points(0)
                        .pkWin(0).pkLose(0).pkDraw(0).streak(0).maxStreak(0).timedFinished(0)
                        .build()));
    }

    @Transactional(readOnly = true)
    public UserPointsProfile getProfile(Long userId) {
        return ensureProfile(userId, currentSeason());
    }

    // ==================== PK 计分 ====================

    /**
     * 结算一场 PK 的积分/连胜。winnerId 为 null 表示平局（双方各 10 分）。
     */
    @Transactional
    public void applyPkResult(Long battleId, Long challengerId, Long opponentId, Long winnerId) {
        String season = currentSeason();
        if (winnerId == null) {
            applyDraw(challengerId, season);
            applyDraw(opponentId, season);
        } else if (winnerId.equals(challengerId)) {
            applyWin(challengerId, season, battleId);
            applyLose(opponentId, season);
        } else {
            applyWin(opponentId, season, battleId);
            applyLose(challengerId, season);
        }
    }

    private void applyWin(Long userId, String season, Long battleId) {
        UserPointsProfile p = ensureProfile(userId, season);
        int winBonusStreak = p.getStreak() + 1; // 含本场
        int bonus = Math.min((winBonusStreak - 1) * 2, 10); // 连胜加成基于此前连胜
        int delta = 25 + bonus;
        p.setPoints(p.getPoints() + delta);
        p.setPkWin(p.getPkWin() + 1);
        p.setStreak(winBonusStreak);
        p.setMaxStreak(Math.max(p.getMaxStreak(), p.getStreak()));
        p.setLastPlayedAt(LocalDateTime.now());
        profileRepository.save(p);
        pushService.profileUpdate(userId, profilePayload(p));
        // 勋章：首战/连胜
        grantBadge(userId, BADGE_FIRST_BLOOD, season, battleId);
        if (p.getStreak() >= 3) grantBadge(userId, BADGE_STREAK3, season, battleId);
        if (p.getStreak() >= 5) grantBadge(userId, BADGE_STREAK5, season, battleId);
    }

    private void applyDraw(Long userId, String season) {
        UserPointsProfile p = ensureProfile(userId, season);
        p.setPoints(p.getPoints() + 10);
        p.setPkDraw(p.getPkDraw() + 1);
        p.setLastPlayedAt(LocalDateTime.now());
        profileRepository.save(p);
        pushService.profileUpdate(userId, profilePayload(p));
        grantBadge(userId, BADGE_FIRST_BLOOD, season, null);
    }

    private void applyLose(Long userId, String season) {
        UserPointsProfile p = ensureProfile(userId, season);
        p.setPoints(p.getPoints() + 5);
        p.setPkLose(p.getPkLose() + 1);
        p.setStreak(0); // 连胜清零
        p.setLastPlayedAt(LocalDateTime.now());
        profileRepository.save(p);
        pushService.profileUpdate(userId, profilePayload(p));
        grantBadge(userId, BADGE_FIRST_BLOOD, season, null);
    }

    // ==================== 限时赛计分 ====================

    /**
     * 限时赛单个完赛者积分结算。rank 从 1 开始，total 为完赛人数。
     *
     * @return 本次加分
     */
    @Transactional
    public int awardTimedPoints(Long userId, Long competitionId, int rank, int total,
                                double score, double totalScore, boolean perfect) {
        String season = currentSeason();
        UserPointsProfile p = ensureProfile(userId, season);
        int part = 5;
        double rate = totalScore > 0 ? Math.min(1.0, score / totalScore) : 0.0;
        int performance = (int) Math.floor(rate * 30);
        performance = Math.min(performance, 30);
        int rankPoints = rankPoints(rank, total);
        int perfectBonus = perfect ? 10 : 0;
        int delta = part + performance + rankPoints + perfectBonus;
        p.setPoints(p.getPoints() + delta);
        p.setTimedFinished(p.getTimedFinished() + 1);
        p.setLastPlayedAt(LocalDateTime.now());
        profileRepository.save(p);
        pushService.profileUpdate(userId, profilePayload(p));
        grantBadge(userId, BADGE_FIRST_BLOOD, season, competitionId);
        if (perfect) grantBadge(userId, BADGE_PERFECT, season, competitionId);
        if (p.getTimedFinished() >= 10) grantBadge(userId, BADGE_VETERAN, season, competitionId);
        return delta;
    }

    private int rankPoints(int rank, int total) {
        if (total <= 1) return 0; // 单人参赛不发名次分
        return switch (rank) {
            case 1 -> 40;
            case 2 -> 30;
            case 3 -> 20;
            default -> rank <= 10 ? 10 : 0;
        };
    }

    // ==================== 悬赏采纳计分 ====================

    /**
     * 悬赏投稿被采纳：给投稿人加 rewardPoints 竞技积分，并发放「悬赏新星」勋章（幂等）。
     * 复用竞赛积分引擎与推送协议（profile_update / badge_granted）。
     *
     * @return 实际加分
     */
    @Transactional
    public int awardBountyPoints(Long userId, Long bountyId, int rewardPoints) {
        String season = currentSeason();
        UserPointsProfile p = ensureProfile(userId, season);
        int delta = Math.max(0, rewardPoints);
        p.setPoints(p.getPoints() + delta);
        p.setLastPlayedAt(LocalDateTime.now());
        profileRepository.save(p);
        pushService.profileUpdate(userId, profilePayload(p));
        grantBadge(userId, BADGE_BOUNTY_STAR, season, bountyId);
        return delta;
    }

    // ==================== 勋章 ====================

    /** 幂等发放勋章（唯一约束兜底），实际新增则推送 badge_granted */
    @Transactional
    public boolean grantBadge(Long userId, String badgeCode, String season, Long relatedId) {
        if (badgeRepository.findByUserIdAndBadgeCodeAndSeason(userId, badgeCode, season).isPresent()) {
            return false;
        }
        String[] def = BADGE_CATALOG.get(badgeCode);
        if (def == null) return false;
        try {
            badgeRepository.save(UserBadge.builder()
                    .userId(userId).badgeCode(badgeCode).season(season)
                    .relatedId(relatedId).grantedAt(LocalDateTime.now()).build());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("badgeCode", badgeCode);
            payload.put("name", def[0]);
            payload.put("icon", def[1]);
            payload.put("season", season);
            pushService.badgeGranted(userId, payload);
            return true;
        } catch (Exception e) {
            // 并发唯一约束冲突：视为已发放
            log.debug("badge already granted user={} code={} ({})", userId, badgeCode, e.getMessage());
            return false;
        }
    }

    private Map<String, Object> profilePayload(UserPointsProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("season", p.getSeason());
        m.put("points", p.getPoints());
        m.put("tier", tierOf(p.getPoints()).name());
        m.put("pkWin", p.getPkWin());
        m.put("pkLose", p.getPkLose());
        m.put("pkDraw", p.getPkDraw());
        m.put("streak", p.getStreak());
        m.put("timedFinished", p.getTimedFinished());
        return m;
    }
}
