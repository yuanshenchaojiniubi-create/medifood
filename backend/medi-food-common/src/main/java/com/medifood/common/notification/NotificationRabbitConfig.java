package com.medifood.common.notification;

import org.springframework.amqp.core.AcknowledgeMode;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通知交换机 / 队列（生产者与消费者使用相同拓扑）
 */
@Configuration
public class NotificationRabbitConfig {

    public static final String EXCHANGE = "medifood.notification.exchange";
    public static final String QUEUE = "medifood.notification.queue";
    public static final String DLQ = "medifood.notification.queue.dlq";
    public static final String DLX = "medifood.notification.exchange.dlx";
    public static final String ROUTING_KEY = "notification.created";

    private static final Logger log = LoggerFactory.getLogger(NotificationRabbitConfig.class);

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey("notification.dlq")
                .build();
    }

    @Bean
    public Queue notificationDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public TopicExchange notificationDlx() {
        return new TopicExchange(DLX, true, false);
    }

    @Bean
    public Binding notificationDlqBinding(Queue notificationDlq, TopicExchange notificationDlx) {
        return BindingBuilder.bind(notificationDlq).to(notificationDlx).with("notification.dlq");
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(ROUTING_KEY);
    }

    /**
     * Spring AMQP 自动装配的 RabbitTemplate 使用此转换器，便于 JSON 序列化 NotificationEvent。
     * @Primary 解决与 EsSyncRabbitConfig 中 esSyncMessageConverter 的 Bean 类型歧义，
     * 确保自动配置的 RabbitTemplate 和 SimpleRabbitListenerContainerFactory 使用 Jackson 序列化。
     */
    @Primary
    @Bean
    public Jackson2JsonMessageConverter notificationJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Primary
    @Bean
    public RabbitTemplate notificationRabbitTemplate(ConnectionFactory connectionFactory,
                                                     @Qualifier("notificationJsonMessageConverter") Jackson2JsonMessageConverter converter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(converter);
        rabbitTemplate.setConfirmCallback((CorrelationData correlationData, boolean ack, String cause) -> {
            if (!ack) {
                log.error("通知消息发送失败: id={}, cause={}",
                        correlationData != null ? correlationData.getId() : "null", cause);
            }
        });
        return rabbitTemplate;
    }

    @Primary
    @Bean
    public SimpleRabbitListenerContainerFactory notificationListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("notificationJsonMessageConverter") Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(1);
        return factory;
    }
}
