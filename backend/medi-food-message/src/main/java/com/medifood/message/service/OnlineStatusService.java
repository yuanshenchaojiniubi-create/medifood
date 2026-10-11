package com.medifood.message.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户在线状态服务
 * <p>
 * 管理所有通过 WebSocket 连接的用户在线状态。
 * 支持心跳保活，超时自动下线。
 * </p>
 */
@Service
@Slf4j
public class OnlineStatusService {

    /**
     * 在线用户集合：userId -> 最后心跳时间
     */
    private final ConcurrentHashMap<Long, LocalDateTime> onlineUsers = new ConcurrentHashMap<>();

    /**
     * 用户上线
     */
    public void userOnline(Long userId) {
        onlineUsers.put(userId, LocalDateTime.now());
        log.info("用户上线: userId={}, 当前在线人数={}", userId, onlineUsers.size());
    }

    /**
     * 用户下线
     */
    public void userOffline(Long userId) {
        onlineUsers.remove(userId);
        log.info("用户下线: userId={}, 当前在线人数={}", userId, onlineUsers.size());
    }

    /**
     * 更新心跳时间
     */
    public void heartbeat(Long userId) {
        onlineUsers.put(userId, LocalDateTime.now());
    }

    /**
     * 判断用户是否在线（含心跳超时检测，超过30秒无心跳视为离线）
     */
    public boolean isOnline(Long userId) {
        LocalDateTime lastHeartbeat = onlineUsers.get(userId);
        if (lastHeartbeat == null) {
            return false;
        }
        // 30秒无心跳视为离线
        if (lastHeartbeat.plusSeconds(30).isBefore(LocalDateTime.now())) {
            onlineUsers.remove(userId);
            return false;
        }
        return true;
    }

    /**
     * 获取所有在线用户ID
     */
    public Set<Long> getOnlineUserIds() {
        // 清理超时用户
        onlineUsers.entrySet().removeIf(entry ->
                entry.getValue().plusSeconds(30).isBefore(LocalDateTime.now()));
        return onlineUsers.keySet();
    }

    /**
     * 获取在线人数
     */
    public int getOnlineCount() {
        return getOnlineUserIds().size();
    }

    /**
     * 获取用户最后活跃时间
     */
    public LocalDateTime getLastActiveTime(Long userId) {
        return onlineUsers.get(userId);
    }
}