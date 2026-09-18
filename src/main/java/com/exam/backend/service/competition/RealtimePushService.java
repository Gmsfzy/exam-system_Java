package com.exam.backend.service.competition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * STOMP 实时推送封装 (v4.0)，替代文档中的 Socket.IO 事件。
 * <p>房间约定：竞赛榜 {@code /topic/competition/{id}}、PK {@code /topic/battle/{id}}、
 * 个人（积分/勋章）{@code /topic/user/{userId}}。推送失败仅记日志，不影响主流程
 * （前端保留轮询兜底）。</p>
 */
@Slf4j
@Service
public class RealtimePushService {

    /** WebSocket starter 缺失时为 null，所有推送降级为 no-op */
    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    public void leaderboardUpdate(Long competitionId, Object payload) {
        send("/topic/competition/" + competitionId, Map.of("event", "leaderboard_update", "data", payload));
    }

    public void battleUpdate(Long battleId, Object payload) {
        send("/topic/battle/" + battleId, Map.of("event", "battle_update", "data", payload));
    }

    public void profileUpdate(Long userId, Object payload) {
        send("/topic/user/" + userId, Map.of("event", "profile_update", "data", payload));
    }

    public void badgeGranted(Long userId, Object payload) {
        send("/topic/user/" + userId, Map.of("event", "badge_granted", "data", payload));
    }

    /** 主观题 AI 判分完成，通知前端刷新成绩（v4.0） */
    public void resultReady(Long userId, Object payload) {
        send("/topic/user/" + userId, Map.of("event", "result_ready", "data", payload));
    }

    private void send(String destination, Object payload) {
        if (messagingTemplate == null) return;
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.warn("stomp push failed dest={} err={}", destination, e.getMessage());
        }
    }
}
