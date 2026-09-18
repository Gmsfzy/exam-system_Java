package com.exam.backend.domain.enums;

import java.util.Set;

public enum QuestionTypeEnum {
    single_choice("单选题"),
    multiple_choice("多选题"),
    fill_blank("填空题"),
    true_false("判断题"),
    short_answer("简答题"),
    programming("编程题"),
    application("应用题"),
    calculation("计算题");

    private static final Set<QuestionTypeEnum> SUBJECTIVE_TYPES = Set.of(
            short_answer, programming, application, calculation);

    private final String label;

    QuestionTypeEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static boolean isSubjective(QuestionTypeEnum type) {
        return type != null && SUBJECTIVE_TYPES.contains(type);
    }

    public boolean isSubjective() {
        return SUBJECTIVE_TYPES.contains(this);
    }

    public static QuestionTypeEnum fromValue(String value) {
        if (value == null) return null;
        try {
            return QuestionTypeEnum.valueOf(value.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
