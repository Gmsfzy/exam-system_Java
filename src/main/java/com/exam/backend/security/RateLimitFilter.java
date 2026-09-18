package com.exam.backend.security;

import com.exam.backend.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API 限流过滤器（对齐文档限流策略）：
 * - 登录/注册: 5次/分钟（按 IP）
 * - AI 出题:   3次/分钟（按用户）
 * - 考试提交:  1次/30秒（按用户）
 * - 其他 /api/**: 60次/分钟（按 IP 或用户）
 * 超限返回 429 + Retry-After / X-RateLimit-* 响应头。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private record Rule(String name, int capacity, Duration period) {}

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        Rule rule = resolveRule(path);
        String identity = resolveIdentity(request);
        Bucket bucket = buckets.computeIfAbsent(rule.name() + ":" + identity,
                k -> newBucket(rule));

        var reservation = bucket.tryConsumeAndReturnRemaining(1);
        if (!reservation.isConsumed()) {
            long waitSeconds = Math.max(1, reservation.getNanosToWaitForRefill() / 1_000_000_000L);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", String.valueOf(waitSeconds));
            response.setHeader("X-RateLimit-Limit", String.valueOf(rule.capacity()));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.error(429, "请求过于频繁，请 " + waitSeconds + " 秒后再试")));
            return;
        }
        response.setHeader("X-RateLimit-Remaining", String.valueOf(reservation.getRemainingTokens()));
        chain.doFilter(request, response);
    }

    private Rule resolveRule(String path) {
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) {
            return new Rule("auth", 5, Duration.ofMinutes(1));
        }
        if (path.startsWith("/api/ai/")) {
            return new Rule("ai", 3, Duration.ofMinutes(1));
        }
        if (path.endsWith("/submit") && path.startsWith("/api/exam/")) {
            return new Rule("exam_submit", 1, Duration.ofSeconds(30));
        }
        return new Rule("default", 60, Duration.ofMinutes(1));
    }

    private String resolveIdentity(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return "u" + principal.getId();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip" + forwarded.split(",")[0].trim();
        }
        return "ip" + request.getRemoteAddr();
    }

    private Bucket newBucket(Rule rule) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(rule.capacity())
                .refillGreedy(rule.capacity(), rule.period())
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
