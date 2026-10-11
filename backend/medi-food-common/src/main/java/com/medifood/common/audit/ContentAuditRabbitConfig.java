package com.medifood.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 内容审核 RabbitMQ 配置（所有微服务共享）
 *
 * <p>放在 common 模块的原因：各内容模块（社区/食谱/知识/问答）
 * 都需要发送审核事件，只有 community 模块消费。
 * RabbitMQ 的队列/交换机声明是幂等的，多服务重复声明无副作用。
 */
@Configuration
public class ContentAuditRabbitConfig {

    public static final String QUEUE = "content.audit.queue";
    public static final String DLQ = "content.audit.queue.dlq";
    public static final String EXCHANGE = "content.audit.exchange";
    public static final String DLX = "content.audit.exchange.dlx";
    public static final String ROUTING_KEY = "content.audit.post";

    private static final Logger log = LoggerFactory.getLogger(ContentAuditRabbitConfig.class);

    @Bean
    public Queue contentAuditQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey("content.audit.dlq")
                .build();
    }

    @Bean
    public Queue contentAuditDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public TopicExchange contentAuditDlx() {
        return new TopicExchange(DLX, true, false);
    }

    @Bean
    public Binding contentAuditDlqBinding(Queue contentAuditDlq, TopicExchange contentAuditDlx) {
        return BindingBuilder.bind(contentAuditDlq).to(contentAuditDlx).with("content.audit.dlq");
    }

    @Bean
    public TopicExchange contentAuditExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Binding contentAuditBinding(Queue contentAuditQueue, TopicExchange contentAuditExchange) {
        return BindingBuilder.bind(contentAuditQueue).to(contentAuditExchange).with(ROUTING_KEY);
    }

    @Bean("auditMessageConverter")
    public MessageConverter auditJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean("auditRabbitTemplate")
    public RabbitTemplate auditRabbitTemplate(ConnectionFactory connectionFactory,
                                              @Qualifier("auditMessageConverter") MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setConfirmCallback((CorrelationData correlationData, boolean ack, String cause) -> {
            if (!ack) {
                log.error("审核消息发送失败: id={}, cause={}",
                        correlationData != null ? correlationData.getId() : "null", cause);
            }
        });
        return rabbitTemplate;
    }

    @Bean("auditListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory auditListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("auditMessageConverter") MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(1);
        // AI 审核单条耗时数秒，开启并发避免审核消息积压
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);
        return factory;
    }
}
