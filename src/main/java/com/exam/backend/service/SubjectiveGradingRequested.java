package com.exam.backend.service;

/**
 * 交卷事务提交后发布的“主观题待 AI 判分”事件（v4.0 异步判分）。
 * 由 {@link SubjectiveGradingService} 以 AFTER_COMMIT + 独立线程池消费，DB 事务绝不横跨 AI 调用。
 *
 * @param sessionId 考试会话 ID（据此定位 examId / studentId）
 */
public record SubjectiveGradingRequested(Long sessionId) {
}
