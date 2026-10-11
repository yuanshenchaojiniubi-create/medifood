package com.medifood.common.sync;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class EsSyncEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public EsSyncEventPublisher(@Qualifier("esSyncRabbitTemplate") RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(EsSyncEvent event) {
        if (event == null || event.getEntityId() == null) {
            return;
        }
        try {
            String routingKey = "es.sync." + event.getEntityType().name().toLowerCase();
            if (event.getMessageId() == null) {
                event.setMessageId(UUID.randomUUID().toString());
            }
            rabbitTemplate.convertAndSend(
                    EsSyncRabbitConfig.EXCHANGE,
                    routingKey,
                    event,
                    new CorrelationData(event.getMessageId())
            );
            log.info("发送ES同步消息成功: type={}, id={}, syncType={}",
                    event.getEntityType(), event.getEntityId(), event.getSyncType());
        } catch (Exception e) {
            log.error("发送ES同步消息失败: type={}, id={}",
                    event.getEntityType(), event.getEntityId(), e);
        }
    }

    public void publishCreate(Long entityId, EsSyncEvent.EntityType entityType, String indexName,
                              Long version, Map<String, Object> payload) {
        publish(EsSyncEvent.create(entityId, entityType, indexName, version, payload));
    }

    public void publishUpdate(Long entityId, EsSyncEvent.EntityType entityType, String indexName,
                              Long version, Map<String, Object> payload) {
        publish(EsSyncEvent.update(entityId, entityType, indexName, version, payload));
    }

    public void publishDelete(Long entityId, EsSyncEvent.EntityType entityType, String indexName,
                              Long version, Map<String, Object> payload) {
        publish(EsSyncEvent.delete(entityId, entityType, indexName, version, payload));
    }
}
