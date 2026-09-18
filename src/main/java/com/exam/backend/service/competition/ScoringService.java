package com.exam.backend.service.competition;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 竞赛计分公式 (v4.0)。
 * <p>答对得分 = 满分×base_ratio + 满分×speed_ratio×max(0, 1 - time_spent/单题均时)。
 * base_ratio/speed_ratio 由 scoring_rule 归一化得到，缺省 0.7 / 0.3。</p>
 */
@Service
public class ScoringService {

    public static final double DEFAULT_BASE = 0.7;
    public static final double DEFAULT_SPEED = 0.3;

    /** 归一化计分规则：保证 base+speed=1；非法/缺省回落 0.7/0.3 */
    public Map<String, Double> normalizeRule(Map<String, Object> raw) {
        double base = DEFAULT_BASE;
        double speed = DEFAULT_SPEED;
        if (raw != null) {
            Object b = raw.get("base_ratio");
            Object s = raw.get("speed_ratio");
            boolean hasBase = b instanceof Number;
            boolean hasSpeed = s instanceof Number;
            double bd = hasBase ? ((Number) b).doubleValue() : Double.NaN;
            double sd = hasSpeed ? ((Number) s).doubleValue() : Double.NaN;
            if (hasBase && hasSpeed && (bd >= 0 || sd >= 0)) {
                // 双边给定时按和归一化，保证 base+speed=1
                double nb = Math.max(0, bd);
                double ns = Math.max(0, sd);
                double sum = nb + ns;
                if (sum > 0) {
                    base = nb / sum;
                    speed = ns / sum;
                }
            } else if (hasBase && bd >= 0 && bd <= 1) {
                // 单边给定：保留给定值，另一端补齐余量
                base = bd;
                speed = 1 - base;
            } else if (hasSpeed && sd >= 0 && sd <= 1) {
                speed = sd;
                base = 1 - speed;
            }
        }
        Map<String, Double> rule = new LinkedHashMap<>();
        rule.put("base_ratio", base);
        rule.put("speed_ratio", speed);
        return rule;
    }

    /**
     * 单题得分。
     *
     * @param fullScore    该题满分
     * @param timeSpent    服务端计算的本答题用时（秒）
     * @param perQuestionSec 单题均时（秒），<=0 时不给速度分
     * @param rule         归一化后的 {base_ratio, speed_ratio}
     */
    public double scoreForAnswer(double fullScore, int timeSpent, double perQuestionSec, Map<String, Double> rule) {
        double baseRatio = rule.getOrDefault("base_ratio", DEFAULT_BASE);
        double speedRatio = rule.getOrDefault("speed_ratio", DEFAULT_SPEED);
        double base = fullScore * baseRatio;
        double speed = 0.0;
        if (perQuestionSec > 0) {
            double ratio = Math.max(0.0, 1.0 - (double) timeSpent / perQuestionSec);
            speed = fullScore * speedRatio * ratio;
        }
        return round2(base + speed);
    }

    /** 0 分（答错） */
    public double zero() {
        return 0.0;
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
