package com.medifood.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 内容审核事件发布器（各内容发布模块统一使用）
 *
 * <p>发布内容后异步投递审核事件，由 community 模块的
 * {@code ContentAuditListener} 消费并调用 AI 审核。
 * 发送失败只记录日志，不影响发布流程（审核漏斗有管理员兜底）。
 */
@Component
public class ContentAuditEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ContentAuditEventPublisher.class);

    @Autowired
    @Qualifier("auditRabbitTemplate")
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送内容审核事件
     *
     * @param event 审核事件（targetId/userId/contentType/content 必填）
     */
    public void publish(ContentAuditEvent event) {
        if (event == null || event.getTargetId() == null || event.getContentType() == null) {
            log.warn("跳过无效审核事件: {}", event);
            return;
        }
        try {
            if (event.getMessageId() == null) {
                event.setMessageId(UUID.randomUUID().toString());
            }
            rabbitTemplate.convertAndSend(
                    ContentAuditRabbitConfig.EXCHANGE,
                    ContentAuditRabbitConfig.ROUTING_KEY,
                    event,
                    new CorrelationData(event.getMessageId()));
            log.info("审核事件已发送: type={}, targetId={}, messageId={}",
                    event.getContentType(), event.getTargetId(), event.getMessageId());
        } catch (Exception e) {
            log.error("发送审核事件失败: type={}, targetId={}",
                    event.getContentType(), event.getTargetId(), e);
        }
    }
}
