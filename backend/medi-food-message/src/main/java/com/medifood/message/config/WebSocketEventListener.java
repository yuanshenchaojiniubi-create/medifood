package com.medifood.message.config;

import com.medifood.message.service.OnlineStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 连接事件监听器
 * <p>
 * 监听 WebSocket 的 CONNECT 和 DISCONNECT 事件，
 * 自动更新用户在线状态，并广播给相关用户。
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final OnlineStatusService onlineStatusService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        if (principal != null) {
            try {
                Long userId = Long.valueOf(principal.getName());
                onlineStatusService.userOnline(userId);
                // 广播在线状态变更
                broadcastStatusChange(userId, true);
            } catch (NumberFormatException e) {
                log.warn("无法解析用户ID: {}", principal.getName());
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        if (principal != null) {
            try {
                Long userId = Long.valueOf(principal.getName());
                onlineStatusService.userOffline(userId);
                // 广播在线状态变更
                broadcastStatusChange(userId, false);
            } catch (NumberFormatException e) {
                log.warn("无法解析用户ID: {}", principal.getName());
            }
        }
    }

    /**
     * 广播用户在线状态变更给所有在线用户
     * <p>
     * 使用 /topic/online.status 公共频道，前端可按需订阅。
     * </p>
     */
    private void broadcastStatusChange(Long userId, boolean online) {
        Map<String, Object> status = new HashMap<>();
        status.put("type", "ONLINE_STATUS");
        status.put("userId", userId);
        status.put("online", online);
        messagingTemplate.convertAndSend("/topic/online.status", status);
    }
}