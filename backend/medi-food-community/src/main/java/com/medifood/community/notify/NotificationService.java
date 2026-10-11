package com.medifood.community.notify;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.common.notification.NotificationEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

public interface NotificationService {
    void register(Long userId, SseEmitter emitter);
    void unregister(Long userId);
    void send(Notification n);
    void aggregateHighFrequency(NotificationEvent event);
    void dispatchDueAggregates();
    void flushAggregatesToDb();
    Page<Map<String, Object>> list(Long userId, Integer page, Integer size);
    void markRead(Long userId, Long id);
    void markAggregateRead(Long userId, String aggregateKey);
    void markAllRead(Long userId);
    long unreadCount(Long userId);
    void delete(Long userId, Long id);
    void deleteAggregate(Long userId, String aggregateKey);

    /**
     * 获取聚合通知的参与者样本（actorId列表，逗号分隔）
     */
    String getAggregateActorSample(String aggregateKey);
}
