package com.exam.backend.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 服务配置 - 绑定 app.ai.* 配置项
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String baseUrl,
        String apiKey,
        String model,
        Integer connectTimeoutMs,
        Integer readTimeoutMs,
        Integer maxRetries
) {
    public AiProperties {
        if (connectTimeoutMs == null) connectTimeoutMs = 30000;
        if (readTimeoutMs == null) readTimeoutMs = 60000;
        if (maxRetries == null) maxRetries = 2;
    }
}
