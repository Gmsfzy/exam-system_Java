package com.exam.backend.common;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record ApiResponse<T>(int code, String message, T data, LocalDateTime timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "success", data, LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    }
}
