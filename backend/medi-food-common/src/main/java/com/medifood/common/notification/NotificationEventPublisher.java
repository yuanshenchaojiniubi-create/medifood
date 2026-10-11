package com.medifood.common.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 将通知事件投递到 RabbitMQ，由社区服务异步落库并推送 SSE
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(NotificationEvent event) {
        if (event == null) {
            return;
        }
        if (event.getRecipientId() == null || event.getActorId() == null) {
            log.warn("跳过无效通知事件: {}", event);
            return;
        }
        if (event.getRecipientId().equals(event.getActorId())) {
            return;
        }
        try {
            if (event.getMessageId() == null) {
                event.setMessageId(UUID.randomUUID().toString());
            }
            rabbitTemplate.convertAndSend(
                    NotificationRabbitConfig.EXCHANGE,
                    NotificationRabbitConfig.ROUTING_KEY,
                    event,
                    new CorrelationData(event.getMessageId())
            );
        } catch (Exception e) {
            log.error("投递通知消息失败 recipientId={}, actorId={}", event.getRecipientId(), event.getActorId(), e);
        }
    }
}
