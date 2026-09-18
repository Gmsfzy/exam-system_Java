package com.exam.backend.config;

import com.exam.backend.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket + STOMP 配置 (v4.0)：替代文档的 Socket.IO 实时层。
 * <p>端点 {@code /ws}（SockJS 兜底）；简单内存代理前缀 {@code /topic}、{@code /queue}；
 * 握手携带 JWT（query {@code ?token=} 或 {@code Authorization} 头）解析出用户 Principal。
 * 房间约定见 {@link com.exam.backend.service.competition.RealtimePushService}。前端保留轮询兜底。</p>
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtProvider jwtProvider;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new JwtHandshakeInterceptor(jwtProvider))
                .withSockJS();
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new JwtHandshakeInterceptor(jwtProvider));
    }

    /** 握手期解析 JWT，将 userId 作为 Principal 名称注入，供用户级目的地使用 */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    static class JwtHandshakeInterceptor implements HandshakeInterceptor {

        private final JwtProvider jwtProvider;

        JwtHandshakeInterceptor(JwtProvider jwtProvider) {
            this.jwtProvider = jwtProvider;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String token = extractToken(request);
            if (token != null && jwtProvider.validateToken(token)) {
                try {
                    Long userId = jwtProvider.parseUserId(token);
                    String role = jwtProvider.parseRole(token);
                    attributes.put("userId", userId);
                    attributes.put("role", role);
                    Principal principal = () -> String.valueOf(userId);
                    attributes.put("principal", principal);
                } catch (Exception e) {
                    log.debug("ws handshake token parse failed: {}", e.getMessage());
                }
            }
            return true; // 未携带 token 也放行，推送降级为不可用，前端轮询兜底
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }

        private String extractToken(ServerHttpRequest request) {
            String authHeader = request.getHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
            if (request instanceof ServletServerHttpRequest servletRequest) {
                String t = servletRequest.getServletRequest().getParameter("token");
                if (t != null && !t.isBlank()) return t;
            }
            String query = request.getURI().getQuery();
            if (query != null) {
                for (String kv : query.split("&")) {
                    if (kv.startsWith("token=")) {
                        String v = kv.substring("token=".length());
                        if (!v.isBlank()) return v;
                    }
                }
            }
            return null;
        }
    }
}
