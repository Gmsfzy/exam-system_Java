package com.exam.backend.util;

import com.exam.backend.domain.enums.QuestionTypeEnum;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 客观题答案比对工具（移植自原 Java 项目 AIService.compareObjectiveAnswer）
 */
@Component
public class AnswerComparator {

    /**
     * 判定客观题是否答对
     */
    public boolean isCorrect(QuestionTypeEnum type, String standardAnswer, String studentAnswer) {
        if (standardAnswer == null || studentAnswer == null) return false;
        String s = standardAnswer.trim();
        String a = studentAnswer.trim();
        if (s.isEmpty() || a.isEmpty()) return false;

        return switch (type) {
            case single_choice -> s.equalsIgnoreCase(a);
            case multiple_choice -> normalizeMulti(s).equalsIgnoreCase(normalizeMulti(a));
            case true_false -> normalizeBoolean(s).equalsIgnoreCase(normalizeBoolean(a));
            case fill_blank -> s.equalsIgnoreCase(a);
            default -> false; // 主观题不算
        };
    }

    /** 多选题：按分隔符拆分后排序再比对 */
    private String normalizeMulti(String answer) {
        List<String> parts = Arrays.stream(answer.split("[,，;；]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .sorted()
                .collect(Collectors.toList());
        return String.join(",", parts);
    }

    /** 判断题: T/F/True/False/对/错/正确/错误 → true/false */
    private String normalizeBoolean(String s) {
        String lower = s.toLowerCase();
        if (lower.equals("t") || lower.equals("true") || s.equals("对") || s.equals("正确")) return "true";
        if (lower.equals("f") || lower.equals("false") || s.equals("错") || s.equals("错误")) return "false";
        return lower;
    }
}
