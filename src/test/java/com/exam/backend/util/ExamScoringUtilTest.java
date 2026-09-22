package com.exam.backend.util;

import com.exam.backend.domain.enums.MultiScoreRuleEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExamScoringUtilTest {

    private final ExamScoringUtil util = new ExamScoringUtil();

    @Test
    @DisplayName("多选判分矩阵：全对/漏选/错选/空答 × 两种规则")
    void scoreMultiChoiceMatrix() {
        // 正确答案 ABC，满分 10
        assertEquals(10.0, util.scoreMultiChoice("A,B,C", "A,B,C", 10, MultiScoreRuleEnum.all_or_nothing), 1e-9);
        assertEquals(10.0, util.scoreMultiChoice("A,B,C", "C,B,A", 10, MultiScoreRuleEnum.all_or_nothing), 1e-9);
        // 漏选：all_or_nothing 得 0；partial 得 答对数/正确数 × 满分
        assertEquals(0.0, util.scoreMultiChoice("A,B,C", "A,B", 10, MultiScoreRuleEnum.all_or_nothing), 1e-9);
        assertEquals(20.0 / 3, util.scoreMultiChoice("A,B,C", "A,B", 10, MultiScoreRuleEnum.partial), 1e-9);
        assertEquals(10.0 / 3, util.scoreMultiChoice("A,B,C", "A", 10, MultiScoreRuleEnum.partial), 1e-9);
        // 含错选：两种规则都 0 分
        assertEquals(0.0, util.scoreMultiChoice("A,B,C", "A,D", 10, MultiScoreRuleEnum.partial), 1e-9);
        assertEquals(0.0, util.scoreMultiChoice("A,B,C", "A,B,C,D", 10, MultiScoreRuleEnum.partial), 1e-9);
        // 空/缺答案
        assertEquals(0.0, util.scoreMultiChoice("A,B,C", "", 10, MultiScoreRuleEnum.partial), 1e-9);
        assertEquals(0.0, util.scoreMultiChoice("A,B,C", null, 10, MultiScoreRuleEnum.all_or_nothing), 1e-9);
    }

    @Test
    @DisplayName("optionMap 正反映射：display→original 与 original→display 互逆")
    void optionMapRoundTrip() {
        // 题 7：显示 A→原 C，显示 B→原 A，显示 C→原 B
        Map<String, Object> optionMap = new HashMap<>();
        optionMap.put("7:A", "C");
        optionMap.put("7:B", "A");
        optionMap.put("7:C", "B");

        assertEquals("C", util.displayToOriginal(7L, "A", optionMap));
        assertEquals("A,C", util.displayToOriginal(7L, "B,A", optionMap));
        // 未登记的 token 原样保留（非字母题/其它题）
        assertEquals("D", util.displayToOriginal(7L, "D", optionMap));
        assertEquals("A", util.displayToOriginal(8L, "A", optionMap));
        assertNull(util.displayToOriginal(7L, null, optionMap));

        // 逆映射回显：原 B→显示 C，原 A→显示 B（与 display→original 严格互逆）
        assertEquals("C", util.originalToDisplay(7L, "B", optionMap));
        assertEquals("B,A", util.originalToDisplay(7L, "A,C", optionMap));

        // 正反映射互逆
        String submitted = "A,C";
        String original = util.displayToOriginal(7L, submitted, optionMap);
        assertEquals(submitted.toUpperCase(), util.originalToDisplay(7L, original, optionMap));
    }

    @Test
    @DisplayName("无乱序会话：optionMap 为空时答案原样透传")
    void noShufflePassthrough() {
        assertEquals("B", util.displayToOriginal(7L, "B", Map.of()));
        assertEquals("B", util.originalToDisplay(7L, "B", null));
        assertNull(util.subMap(7L, Map.of()));
    }
}
