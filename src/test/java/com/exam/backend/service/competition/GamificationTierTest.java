package com.exam.backend.service.competition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 段位推导单元测试 (v4.0)：青铜0/白银100/黄金250/铂金450/钻石700/王者1000。
 * tierOf/nextTierOf 为纯函数，依赖仓储以 null 注入即可。
 */
class GamificationTierTest {

    private GamificationService gamification;

    @BeforeEach
    void setUp() {
        gamification = new GamificationService(null, null, null);
    }

    @Test
    @DisplayName("各阈值边界落在正确段位")
    void tierBoundaries() {
        assertThat(gamification.tierOf(0).name()).isEqualTo("青铜");
        assertThat(gamification.tierOf(99).name()).isEqualTo("青铜");
        assertThat(gamification.tierOf(100).name()).isEqualTo("白银");
        assertThat(gamification.tierOf(249).name()).isEqualTo("白银");
        assertThat(gamification.tierOf(250).name()).isEqualTo("黄金");
        assertThat(gamification.tierOf(449).name()).isEqualTo("黄金");
        assertThat(gamification.tierOf(450).name()).isEqualTo("铂金");
        assertThat(gamification.tierOf(699).name()).isEqualTo("铂金");
        assertThat(gamification.tierOf(700).name()).isEqualTo("钻石");
        assertThat(gamification.tierOf(999).name()).isEqualTo("钻石");
        assertThat(gamification.tierOf(1000).name()).isEqualTo("王者");
        assertThat(gamification.tierOf(5000).name()).isEqualTo("王者");
    }

    @Test
    @DisplayName("段位图标非空")
    void tierHasIcon() {
        assertThat(gamification.tierOf(1000).icon()).isEqualTo("👑");
    }

    @Test
    @DisplayName("下一段位：按分数返回升段目标")
    void nextTier() {
        assertThat(gamification.nextTierOf(0).name()).isEqualTo("白银");
        assertThat(gamification.nextTierOf(150).name()).isEqualTo("黄金");
        assertThat(gamification.nextTierOf(999).name()).isEqualTo("王者");
    }

    @Test
    @DisplayName("最高段位之上：nextTier 为 null")
    void nextTierAtTop() {
        assertThat(gamification.nextTierOf(1000)).isNull();
        assertThat(gamification.nextTierOf(99999)).isNull();
    }

    @Test
    @DisplayName("勋章目录包含悬赏新星")
    void badgeCatalogContainsBountyStar() {
        assertThat(GamificationService.badgeCatalog()).containsKey(GamificationService.BADGE_BOUNTY_STAR);
    }
}
