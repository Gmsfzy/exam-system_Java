package com.exam.backend.service.competition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 竞赛计分公式单元测试 (v4.0)。
 */
class ScoringServiceTest {

    private ScoringService scoring;

    @BeforeEach
    void setUp() {
        scoring = new ScoringService();
    }

    @Test
    @DisplayName("归一化：缺省回落 0.7 / 0.3")
    void normalizeDefault() {
        Map<String, Double> rule = scoring.normalizeRule(null);
        assertThat(rule.get("base_ratio")).isCloseTo(0.7, within(1e-9));
        assertThat(rule.get("speed_ratio")).isCloseTo(0.3, within(1e-9));

        Map<String, Double> empty = scoring.normalizeRule(new HashMap<>());
        assertThat(empty.get("base_ratio")).isCloseTo(0.7, within(1e-9));
        assertThat(empty.get("speed_ratio")).isCloseTo(0.3, within(1e-9));
    }

    @Test
    @DisplayName("归一化：base+speed 强制为 1")
    void normalizeSumsToOne() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("base_ratio", 0.8);
        raw.put("speed_ratio", 0.8);
        Map<String, Double> rule = scoring.normalizeRule(raw);
        assertThat(rule.get("base_ratio") + rule.get("speed_ratio")).isCloseTo(1.0, within(1e-9));
        assertThat(rule.get("base_ratio")).isCloseTo(0.5, within(1e-9));
        assertThat(rule.get("speed_ratio")).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("归一化：单边缺省时补齐另一端")
    void normalizeSingleSided() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("base_ratio", 0.6);
        Map<String, Double> rule = scoring.normalizeRule(raw);
        assertThat(rule.get("base_ratio")).isCloseTo(0.6, within(1e-9));
        assertThat(rule.get("speed_ratio")).isCloseTo(0.4, within(1e-9));
    }

    @Test
    @DisplayName("归一化：非法值（全负/零）回落默认")
    void normalizeInvalidFallback() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("base_ratio", -1);
        raw.put("speed_ratio", 0);
        Map<String, Double> rule = scoring.normalizeRule(raw);
        assertThat(rule.get("base_ratio")).isCloseTo(0.7, within(1e-9));
        assertThat(rule.get("speed_ratio")).isCloseTo(0.3, within(1e-9));
    }

    @Test
    @DisplayName("单题得分：满分 + 快速奖励")
    void scoreFastAnswer() {
        Map<String, Double> rule = scoring.normalizeRule(null); // 0.7/0.3
        // 满分 100，用时 0，均时 60 → base=70 + speed=30*(1-0)=30 → 100
        assertThat(scoring.scoreForAnswer(100, 0, 60, rule)).isCloseTo(100.0, within(1e-6));
    }

    @Test
    @DisplayName("单题得分：刚好用尽均时只得基础分")
    void scoreSlowAnswer() {
        Map<String, Double> rule = scoring.normalizeRule(null);
        // 满分 100，用时=均时 60 → base=70 + speed=0 → 70
        assertThat(scoring.scoreForAnswer(100, 60, 60, rule)).isCloseTo(70.0, within(1e-6));
    }

    @Test
    @DisplayName("单题得分：超时无速度分（不为负）")
    void scoreOvertime() {
        Map<String, Double> rule = scoring.normalizeRule(null);
        // 用时 120 > 均时 60 → ratio 归 0 → 仅 base 70
        assertThat(scoring.scoreForAnswer(100, 120, 60, rule)).isCloseTo(70.0, within(1e-6));
    }

    @Test
    @DisplayName("单题得分：无均时（<=0）只给基础分")
    void scoreNoPerQuestion() {
        Map<String, Double> rule = scoring.normalizeRule(null);
        assertThat(scoring.scoreForAnswer(100, 5, 0, rule)).isCloseTo(70.0, within(1e-6));
    }

    @Test
    @DisplayName("答错恒为 0 分")
    void zero() {
        assertThat(scoring.zero()).isZero();
    }
}
