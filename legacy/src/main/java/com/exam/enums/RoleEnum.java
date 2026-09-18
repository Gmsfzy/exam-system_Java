package com.exam.enums;

import java.util.Arrays;
import java.util.List;

public enum RoleEnum {
    TEACHER("teacher", "教师"),
    STUDENT("student", "学生");

    private final String value;
    private final String label;

    RoleEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static RoleEnum fromValue(String value) {
        for (RoleEnum r : values()) {
            if (r.value.equalsIgnoreCase(value)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}