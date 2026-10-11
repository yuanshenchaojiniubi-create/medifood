package com.medifood.community.notify;

import com.medifood.common.notification.NotificationEvent;
import com.medifood.common.notification.NotificationRabbitConfig;
import com.medifood.common.util.IdempotencyUtil;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 消费 RabbitMQ 中的通知事件，落库并 SSE 推送（与 NotificationServiceImpl.send 行为一致）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationQueueListener {

    private final NotificationServiceImpl notificationService;
    private final IdempotencyUtil idempotencyUtil;

    @RabbitListener(queues = NotificationRabbitConfig.QUEUE, containerFactory = "notificationListenerContainerFactory")
    public void onNotification(NotificationEvent event, Message message, Channel channel) {
        if (event == null || event.getRecipientId() == null || event.getActorId() == null) {
            basicAck(channel, message);
            return;
        }
        if (event.getRecipientId().equals(event.getActorId())) {
            basicAck(channel, message);
            return;
        }
        if (idempotencyUtil.isConsumed(event.getMessageId())) {
            log.info("通知消息已消费（幂等跳过）: messageId={}", event.getMessageId());
            basicAck(channel, message);
            return;
        }
        try {
            // 高频低价值事件走 Redis 聚合 + 微批推送
            if (isHighFrequency(event.getType())) {
                notificationService.aggregateHighFrequency(event);
                basicAck(channel, message);
                return;
            }
            // 强通知事件直接落库并实时 SSE
            Notification n = new Notification();
            n.setRecipientId(event.getRecipientId());
            n.setActorId(event.getActorId());
            n.setType(event.getType());
            n.setEntityType(event.getEntityType());
            n.setEntityId(event.getEntityId());
            n.setExtraId(event.getExtraId() != null ? event.getExtraId() : 0L);
            n.setIsRead(0);
            n.setCreateTime(new Date());
            notificationService.send(n);
        } catch (Exception e) {
            log.error("处理通知消息失败 event={}", event, e);
            basicNack(channel, message);
            return;
        }
        basicAck(channel, message);
    }

    private boolean isHighFrequency(Integer type) {
        return type != null && (type == 1 || type == 5);
    }

    private void basicAck(Channel channel, Message message) {
        try {
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("通知消息确认失败", e);
        }
    }

    private void basicNack(Channel channel, Message message) {
        try {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
        } catch (Exception e) {
            log.error("通知消息拒绝失败", e);
        }
    }
}
