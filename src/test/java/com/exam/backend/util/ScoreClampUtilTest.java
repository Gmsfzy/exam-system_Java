package com.exam.backend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreClampUtilTest {

    @Test
    @DisplayName("正常值保持不变")
    void normalValueUnchanged() {
        assertThat(ScoreClampUtil.clamp(8.0, 10.0)).isEqualTo(8.0);
    }

    @Test
    @DisplayName("超过上限截断到上限")
    void clampToMax() {
        assertThat(ScoreClampUtil.clamp(12.5, 10.0)).isEqualTo(10.0);
    }

    @Test
    @DisplayName("低于下限截断到 0")
    void clampToZero() {
        assertThat(ScoreClampUtil.clamp(-3.0, 10.0)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("负上限被视为 0")
    void negativeMaxTreatedAsZero() {
        assertThat(ScoreClampUtil.clamp(1.0, -5.0)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("边界值 0 与满分")
    void boundaryValues() {
        assertThat(ScoreClampUtil.clamp(0.0, 10.0)).isEqualTo(0.0);
        assertThat(ScoreClampUtil.clamp(10.0, 10.0)).isEqualTo(10.0);
    }
}
