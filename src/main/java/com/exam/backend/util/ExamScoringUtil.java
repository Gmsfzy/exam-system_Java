package com.exam.backend.util;

import com.exam.backend.domain.enums.MultiScoreRuleEnum;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * M6 考务判分原语：选项乱序映射（display→original）与多选题得分规则。
 * <p>optionMap 为会话级扁平结构：键 "questionId:显示字母"，值 原字母。</p>
 */
@Component
public class ExamScoringUtil {

    /** 从扁平 optionMap 中提取某题的 "qid:字母" -> 字母 子映射；无则返回 null */
    public Map<String, String> subMap(Long questionId, Map<String, Object> optionMap) {
        if (optionMap == null || optionMap.isEmpty() || questionId == null) return null;
        String prefix = questionId + ":";
        Map<String, String> sub = new LinkedHashMap<>();
        optionMap.forEach((k, v) -> {
            if (k != null && k.startsWith(prefix) && v instanceof String s) sub.put(k, s);
        });
        return sub.isEmpty() ? null : sub;
    }

    /** 学生提交的显示字母答案 → 原始字母（判分前调用） */
    public String displayToOriginal(Long questionId, String submitted, Map<String, Object> optionMap) {
        return translate(questionId, submitted, subMap(questionId, optionMap));
    }

    /** 库内原始字母答案 → 显示字母（乱序卷回显下发前调用，用逆映射） */
    public String originalToDisplay(Long questionId, String saved, Map<String, Object> optionMap) {
        Map<String, String> m = subMap(questionId, optionMap);
        if (m == null || saved == null || saved.isBlank()) return saved;
        String prefix = questionId + ":";
        // 正向："qid:显示字母" -> 原字母；逆向供回显："qid:原字母" -> 显示字母
        Map<String, String> inverse = new HashMap<>();
        m.forEach((k, v) -> inverse.put(prefix + v, k.substring(prefix.length())));
        return translate(questionId, saved, inverse);
    }

    private String translate(Long questionId, String value, Map<String, String> m) {
        if (value == null || value.isBlank() || m == null || m.isEmpty()) return value;
        List<String> tokens = new ArrayList<>();
        for (String raw : value.split("[,，;；]")) {
            String t = raw.trim();
            if (t.isEmpty()) continue;
            String mapped = m.get(questionId + ":" + t.toUpperCase());
            tokens.add(mapped == null ? t : mapped);
        }
        return tokens.isEmpty() ? value : String.join(",", tokens);
    }

    /**
     * 多选得分：全对满分；partial 规则下漏选按 答对数/正确选项数 比例给分，含错选 0 分；
     * all_or_nothing 规则下非全对 0 分。
     */
    public double scoreMultiChoice(String standard, String student, double maxScore, MultiScoreRuleEnum rule) {
        if (standard == null || student == null || student.isBlank()) return 0.0;
        List<String> correct = splitLetters(standard);
        List<String> picked = splitLetters(student);
        if (correct.isEmpty() || picked.isEmpty()) return 0.0;
        Set<String> correctSet = new HashSet<>(correct);
        boolean hasWrong = picked.stream().anyMatch(x -> !correctSet.contains(x));
        long hit = picked.stream().distinct().filter(correctSet::contains).count();
        boolean all = hit == correct.size() && !hasWrong;
        if (all) return maxScore;
        if (rule == MultiScoreRuleEnum.partial && !hasWrong) {
            return maxScore * hit / correct.size();
        }
        return 0.0;
    }

    /** 答案串拆成字母 token（大写、去空白与常见分隔符） */
    public List<String> splitLetters(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String p : s.toUpperCase().split("[,，;；\\s]+")) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** 选项下标 → 字母（0=A） */
    public String letter(int i) {
        return String.valueOf((char) ('A' + i));
    }
}
