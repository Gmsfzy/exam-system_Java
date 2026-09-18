package com.exam.backend.util;

public final class ScoreClampUtil {

    private ScoreClampUtil() {}

    /** 将 AI 评分 clamp 到 [0, max] 区间 */
    public static double clamp(double score, double max) {
        double min = 0.0;
        if (max < 0) max = 0.0;
        if (score < min) return min;
        if (score > max) return max;
        return score;
    }
}
