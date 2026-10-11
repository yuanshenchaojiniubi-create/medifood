package com.medifood.common.sync;

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
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class EsSyncRabbitConfig {

    public static final String EXCHANGE = "medifood.es.sync.exchange";
    public static final String QUEUE = "medifood.es.sync.queue";
    public static final String DLQ = "medifood.es.sync.queue.dlq";
    public static final String DLX = "medifood.es.sync.exchange.dlx";
    public static final String ROUTING_KEY = "es.sync.#";

    private static final Logger log = LoggerFactory.getLogger(EsSyncRabbitConfig.class);

    @Bean
    public TopicExchange esSyncExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue esSyncQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey("es.sync.dlq")
                .build();
    }

    @Bean
    public Queue esSyncDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public TopicExchange esSyncDlx() {
        return new TopicExchange(DLX, true, false);
    }

    @Bean
    public Binding esSyncDlqBinding(Queue esSyncDlq, TopicExchange esSyncDlx) {
        return BindingBuilder.bind(esSyncDlq).to(esSyncDlx).with("es.sync.dlq");
    }

    @Bean
    public Binding esSyncBinding(Queue esSyncQueue, TopicExchange esSyncExchange) {
        return BindingBuilder.bind(esSyncQueue).to(esSyncExchange).with(ROUTING_KEY);
    }

    @Bean("esSyncMessageConverter")
    public MessageConverter esSyncJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean("esSyncRabbitTemplate")
    public RabbitTemplate esSyncRabbitTemplate(ConnectionFactory connectionFactory,
                                               @Qualifier("esSyncMessageConverter") MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setConfirmCallback((CorrelationData correlationData, boolean ack, String cause) -> {
            if (!ack) {
                log.error("ES同步消息发送失败: id={}, cause={}",
                        correlationData != null ? correlationData.getId() : "null", cause);
            }
        });
        return rabbitTemplate;
    }

    @Bean("esSyncListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory esSyncListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("esSyncMessageConverter") MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(1);
        return factory;
    }
}