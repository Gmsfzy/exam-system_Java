package com.exam.enums;

public enum DifficultyEnum {
    EASY("easy", "简单"),
    MEDIUM("medium", "中等"),
    HARD("hard", "困难");

    private final String value;
    private final String label;

    DifficultyEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static DifficultyEnum fromValue(String value) {
        for (DifficultyEnum d : values()) {
            if (d.value.equalsIgnoreCase(value) || d.label.equals(value)) {
                return d;
            }
        }
        return MEDIUM;
    }
}