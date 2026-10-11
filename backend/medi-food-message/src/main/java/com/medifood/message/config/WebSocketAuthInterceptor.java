package com.medifood.message.config;

import com.medifood.common.util.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.security.Principal;
import java.util.List;

/**
 * WebSocket(STOMP)连接鉴权。
 *
 * <p>使用与登录签发一致的 {@link JwtUtils} 校验 Token（登录接口通过 JwtUtils 签发 Access Token，
 * 旧实现误用 JwtUtil 导致密钥不一致、所有 WebSocket 连接都被拒绝）。
 */
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;

    public WebSocketAuthInterceptor(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            String token = null;

            if (authHeaders != null && !authHeaders.isEmpty()) {
                String header = authHeaders.get(0);
                if (header.startsWith("Bearer ")) {
                    token = header.substring(7);
                } else {
                    token = header;
                }
            }

            if (token == null && accessor.getFirstNativeHeader("token") != null) {
                token = accessor.getFirstNativeHeader("token");
            }

            if (token == null) {
                log.warn("WebSocket 连接缺少 Token");
                return null;
            }

            try {
                String userId = jwtUtils.getUserIdFromToken(token);
                if (userId == null || userId.isEmpty()) {
                    log.warn("WebSocket Token 无效");
                    return null;
                }
                Principal principal = userId::toString;
                accessor.setUser(principal);
                log.info("WebSocket 认证成功: userId={}", userId);
            } catch (Exception e) {
                log.error("WebSocket Token 解析失败: {}", e.getMessage());
                return null;
            }
        }

        return message;
    }
}
