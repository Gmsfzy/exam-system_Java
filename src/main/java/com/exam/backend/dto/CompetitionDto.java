package com.exam.backend.dto;

import com.exam.backend.domain.enums.QuestionTypeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 竞赛 / 竞技化 / 战队 DTO (v4.0)。全部 record，字段 camelCase。
 */
public final class CompetitionDto {

    private CompetitionDto() {}

    // ==================== 管理 ====================

    public record CompetitionRequest(
            @NotBlank(message = "标题不能为空") String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer duration,
            Integer drawCount,
            Boolean allowPk,
            Map<String, Object> scoringRule
    ) {}

    public record AddQuestionsRequest(@NotNull List<Long> questionIds) {}

    public record CompetitionView(
            Long id, String title, String description, String competitionType, String status,
            LocalDateTime startTime, LocalDateTime endTime, Integer duration, Integer drawCount,
            Double totalScore, Boolean allowPk, Map<String, Object> scoringRule,
            Long creatorId, long participantCount, LocalDateTime createdAt
    ) {}

    /** 管理端题目视图（含答案，仅教师） */
    public record CqAdminView(
            Long id, Long questionId, QuestionTypeEnum qType, String qContent,
            List<String> options, String answer, Double score, Integer order
    ) {}

    public record CompetitionDetailVO(CompetitionView competition, List<CqAdminView> questions) {}

    public record ParticipantView(
            Long id, Long userId, String username, String status,
            Double score, Integer usedTime, LocalDateTime startedAt, LocalDateTime finishedAt
    ) {}

    // ==================== 限时赛 ====================

    public record LobbyItem(
            Long id, String title, String competitionType, String status,
            LocalDateTime startTime, LocalDateTime endTime, Integer duration, Integer drawCount,
            Double totalScore, Boolean allowPk, long participantCount,
            boolean joined, Long myParticipantId, Integer myRank
    ) {}

    /** 答题页题目（无答案） */
    public record PlayQuestion(
            Long cqId, QuestionTypeEnum qType, String content,
            List<String> options, Double score, Integer order
    ) {}

    public record AnsweredView(Long cqId, String answer, Boolean isCorrect, Double gainedScore, Integer timeSpent) {}

    public record StartResponse(
            Long competitionId, Long participantId, LocalDateTime deadline,
            List<PlayQuestion> questions
    ) {}

    public record PlayStateResponse(
            String status, Double score, long answeredCount, long totalCount,
            Integer remainingSec, LocalDateTime deadline,
            List<PlayQuestion> questions, List<AnsweredView> answers
    ) {}

    public record AnswerRequest(@NotNull Long cqId, String answer) {}

    public record AnswerResponse(
            Long cqId, boolean isCorrect, double gainedScore, String correctAnswer, double totalScore
    ) {}

    public record FinishResponse(
            Long competitionId, Double score, Integer usedTime, int rank, int total
    ) {}

    public record LeaderboardEntry(int rank, Long userId, String username, Double score, Integer usedTime) {}

    public record LeaderboardResponse(List<LeaderboardEntry> entries, Integer myRank, LeaderboardEntry myEntry) {}

    // ==================== PK ====================

    public record PkCreateRequest(Long opponentId) {}

    public record PkBattleView(
            Long id, Long competitionId, String competitionTitle,
            Long challengerId, String challengerName, Long opponentId, String opponentName,
            String status, Long winnerId,
            Double challengerScore, Double opponentScore,
            Integer challengerAnswered, Integer opponentAnswered,
            LocalDateTime startedAt, LocalDateTime finishedAt, LocalDateTime createdAt
    ) {}

    public record PkLobbyResponse(List<PkBattleView> waiting, List<PkBattleView> mine) {}

    public record PkStateResponse(
            PkBattleView battle, String myRole, String myStatus,
            List<PlayQuestion> questions, List<AnsweredView> myAnswers,
            Integer remainingSec, LocalDateTime deadline
    ) {}

    public record PkAnswerRequest(@NotNull Long cqId, String answer) {}

    public record PkAnswerResponse(
            Long cqId, boolean isCorrect, double gainedScore, String correctAnswer,
            double myScore, double opponentScore
    ) {}

    // ==================== 竞技化 ====================

    public record RankMeView(
            String season, int points, String tier, String tierIcon,
            String nextTier, Integer nextTierThreshold, Integer progressToNext,
            int pkWin, int pkLose, int pkDraw, double winRate,
            int streak, int maxStreak, int timedFinished, Integer mySeasonRank
    ) {}

    public record SeasonRankEntry(int rank, Long userId, String username, int points,
                                  String tier, int pkWin, int timedFinished) {}

    public record SeasonRankView(String season, List<SeasonRankEntry> entries, Integer myRank) {}

    public record ArchiveView(String season, Integer rank, Integer points, String tier,
                              Integer pkWin, Integer pkTotal, Integer timedFinished) {}

    public record BadgeView(String badgeCode, String name, String icon, String description,
                            String season, boolean earned, LocalDateTime grantedAt) {}

    public record ProfileDimension(String key, String label, double value, double max) {}

    public record StudyProfileView(
            Long userId, String username, String season, int points, String tier,
            double overall, List<ProfileDimension> dimensions,
            Map<String, Object> raw
    ) {}

    // ==================== 战队 ====================

    public record TeamRequest(@NotBlank(message = "战队名称不能为空") String name, String description) {}

    public record TeamMemberView(Long userId, String username, String role, LocalDateTime joinedAt) {}

    public record TeamView(
            Long id, String name, String description, Long captainId, String captainName,
            int memberCount, LocalDateTime createdAt, List<TeamMemberView> members, boolean mine
    ) {}

    public record TransferRequest(@NotNull Long userId) {}
}
