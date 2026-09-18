package com.exam.enums;

public enum SessionStatusEnum {
    IN_PROGRESS("in_progress", "进行中"),
    SUBMITTED("submitted", "已提交"),
    ENDED("ended", "已结束");

    private final String value;
    private final String label;

    SessionStatusEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static SessionStatusEnum fromValue(String value) {
        for (SessionStatusEnum s : values()) {
            if (s.value.equalsIgnoreCase(value)) {
                return s;
            }
        }
        return IN_PROGRESS;
    }
}