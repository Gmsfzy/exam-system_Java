package com.exam.enums;

public enum ExamStatusEnum {
    DRAFT("draft", "草稿"),
    PUBLISHED("published", "已发布"),
    ENDED("ended", "已结束");

    private final String value;
    private final String label;

    ExamStatusEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static ExamStatusEnum fromValue(String value) {
        for (ExamStatusEnum e : values()) {
            if (e.value.equalsIgnoreCase(value)) {
                return e;
            }
        }
        return DRAFT;
    }
}