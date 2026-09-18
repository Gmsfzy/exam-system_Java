package com.exam.enums;

import java.util.Arrays;
import java.util.List;

public enum QuestionTypeEnum {
    SINGLE_CHOICE("single_choice", "单选题"),
    MULTIPLE_CHOICE("multiple_choice", "多选题"),
    FILL_BLANK("fill_blank", "填空题"),
    TRUE_FALSE("true_false", "判断题"),
    SHORT_ANSWER("short_answer", "简答题"),
    PROGRAMMING("programming", "编程题"),
    APPLICATION("application", "应用题"),
    CALCULATION("calculation", "计算题");

    private static final List<QuestionTypeEnum> SUBJECTIVE_TYPES = Arrays.asList(
            SHORT_ANSWER, PROGRAMMING, APPLICATION, CALCULATION);

    private final String value;
    private final String label;

    QuestionTypeEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static QuestionTypeEnum fromValue(String value) {
        for (QuestionTypeEnum q : values()) {
            if (q.value.equalsIgnoreCase(value) || q.label.equals(value)) {
                return q;
            }
        }
        throw new IllegalArgumentException("Unknown question type: " + value);
    }

    public static boolean isSubjective(QuestionTypeEnum type) {
        return SUBJECTIVE_TYPES.contains(type);
    }

    public boolean isSubjective() {
        return isSubjective(this);
    }

    public static List<QuestionTypeEnum> getSubjectiveTypes() {
        return SUBJECTIVE_TYPES;
    }
}