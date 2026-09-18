package com.exam.backend.common.exception;

public enum ErrorCode {
    AUTH_FAIL(401, "认证失败"),
    FORBIDDEN(403, "无权操作"),
    NOT_FOUND(404, "资源不存在"),
    VALIDATION_ERROR(400, "参数校验失败"),
    CONFLICT(409, "冲突"),
    AI_CALL_FAILED(502, "AI 服务调用失败"),
    BUSINESS_ERROR(400, "业务异常"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int http;
    private final String defaultMsg;

    ErrorCode(int http, String defaultMsg) {
        this.http = http;
        this.defaultMsg = defaultMsg;
    }

    public int getHttp() { return http; }
    public String getDefaultMsg() { return defaultMsg; }
}
