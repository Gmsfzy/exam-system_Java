package com.exam.backend.domain.entity;

import com.exam.backend.domain.enums.ExamStatusEnum;
import com.exam.backend.domain.enums.MultiScoreRuleEnum;
import com.exam.backend.domain.enums.PaperModeEnum;
import com.exam.backend.domain.enums.ScoreStrategyEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "exam")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Exam extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column
    private Integer duration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ExamStatusEnum status = ExamStatusEnum.draft;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "invitation_code", length = 64)
    private String invitationCode;

    @Column(name = "invitation_url", length = 256)
    private String invitationUrl;

    // ==== M6 考务升级字段（存量行加列后为 null，读取一律走下方 *Effective() 兜底方法） ====

    /** 成绩是否已发布；false 时学生端看不到总分，需教师手动发布 */
    @Column(name = "results_published")
    @Builder.Default
    private Boolean resultsPublished = true;

    /** 允许考试次数上限（含补考授权） */
    @Column(name = "max_attempts")
    @Builder.Default
    private Integer maxAttempts = 1;

    /** 多轮次成绩策略：last=最新一轮，best=最优一轮 */
    @Enumerated(EnumType.STRING)
    @Column(name = "score_strategy", length = 16)
    @Builder.Default
    private ScoreStrategyEnum scoreStrategy = ScoreStrategyEnum.last;

    /** 组卷模式：unified=统一卷，random=个人随机抽题 */
    @Enumerated(EnumType.STRING)
    @Column(name = "paper_mode", length = 16)
    @Builder.Default
    private PaperModeEnum paperMode = PaperModeEnum.unified;

    /** random 模式抽题数（0 或 null=全部） */
    @Column(name = "random_count")
    @Builder.Default
    private Integer randomCount = 0;

    /** 是否打乱选项顺序（生成会话级 optionMap） */
    @Column(name = "shuffle_options")
    @Builder.Default
    private Boolean shuffleOptions = false;

    /** 多选题判分规则：all_or_nothing / partial */
    @Enumerated(EnumType.STRING)
    @Column(name = "multi_score_rule", length = 16)
    @Builder.Default
    private MultiScoreRuleEnum multiScoreRule = MultiScoreRuleEnum.all_or_nothing;

    /** 是否匿名阅卷（阅卷列表以学生化名展示） */
    @Column(name = "anonymous_grading")
    @Builder.Default
    private Boolean anonymousGrading = false;

    public boolean resultsPublishedEffective() {
        return resultsPublished == null || resultsPublished;
    }

    public int maxAttemptsEffective() {
        return maxAttempts == null || maxAttempts < 1 ? 1 : maxAttempts;
    }

    /** random 抽题数：null/非正数 = 全部 */
    public int randomCountOrAll() {
        return randomCount == null || randomCount <= 0 ? Integer.MAX_VALUE : randomCount;
    }

    public ScoreStrategyEnum scoreStrategyEffective() {
        return scoreStrategy == null ? ScoreStrategyEnum.last : scoreStrategy;
    }

    public PaperModeEnum paperModeEffective() {
        return paperMode == null ? PaperModeEnum.unified : paperMode;
    }

    public boolean shuffleOptionsEffective() {
        return Boolean.TRUE.equals(shuffleOptions);
    }

    public MultiScoreRuleEnum multiScoreRuleEffective() {
        return multiScoreRule == null ? MultiScoreRuleEnum.all_or_nothing : multiScoreRule;
    }

    public boolean anonymousGradingEffective() {
        return Boolean.TRUE.equals(anonymousGrading);
    }
}
