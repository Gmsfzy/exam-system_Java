package com.exam.backend.client;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 火山方舟（豆包）API 客户端
 * - 调用 chat/completions 接口
 * - 解析 choices[0].message.content 内嵌 JSON
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArkAiClient {

    private final RestClient arkRestClient;
    private final AiProperties props;
    private final ObjectMapper objectMapper;

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE =
            new ParameterizedTypeReference<>() {};

    /**
     * 调用 AI 生成文本（统一入口）
     * @param systemPrompt 系统提示
     * @param userPrompt   用户提示
     * @param temperature  采样温度
     * @return AI 返回的原始 content 字符串
     */
    public String chat(String systemPrompt, String userPrompt, double temperature) {
        Map<String, Object> body = Map.of(
                "model", props.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", temperature,
                "max_tokens", 2000);

        Exception lastEx = null;
        for (int attempt = 0; attempt <= props.maxRetries(); attempt++) {
            try {
                Map<String, Object> resp = arkRestClient.post()
                        .header("Authorization", "Bearer " + props.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(MAP_RESPONSE);
                return extractContent(resp);
            } catch (Exception e) {
                lastEx = e;
                log.warn("AI call attempt {} failed: {}", attempt + 1, e.getMessage());
            }
        }
        throw new BusinessException(ErrorCode.AI_CALL_FAILED,
                "AI 服务调用失败: " + (lastEx == null ? "unknown" : lastEx.getMessage()));
    }

    /**
     * 调用 AI 并将 content 解析为指定类型对象
     */
    public <T> T chatForObject(String systemPrompt, String userPrompt, double temperature, TypeReference<T> type) {
        String content = chat(systemPrompt, userPrompt, temperature);
        try {
            // 兼容 AI 返回带 ```json 围栏的情况
            String json = stripFence(content);
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("Parse AI content as JSON failed: {}, content={}", e.getMessage(), content);
            throw new BusinessException(ErrorCode.AI_CALL_FAILED, "AI 返回内容解析失败");
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> resp) {
        if (resp == null) throw new BusinessException(ErrorCode.AI_CALL_FAILED, "AI 响应为空");
        Object choicesObj = resp.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_CALL_FAILED, "AI 响应无 choices");
        }
        Map<String, Object> first = (Map<String, Object>) choices.get(0);
        Map<String, Object> message = (Map<String, Object>) first.get("message");
        Object content = message.get("content");
        return content == null ? "" : content.toString();
    }

    private String stripFence(String content) {
        if (content == null) return "[]";
        String s = content.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) s = s.substring(firstNl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }
}