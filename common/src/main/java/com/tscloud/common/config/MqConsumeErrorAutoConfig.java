package com.tscloud.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.rabbitmq.listener.simple.retry.enabled", havingValue = "true")
public class MqConsumeErrorAutoConfig {

    @Value("${spring.application.name}")
    private String serviceName;

    @Bean
    public DirectExchange errorExchange() {
        return ExchangeBuilder.directExchange("error.direct")
                .durable(true)
                .build();
    }

    @Bean
    public Queue serviceErrorQueue() {
        String queueName = serviceName + ".error.queue";
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding errorQueueBinding(DirectExchange errorExchange, Queue serviceErrorQueue) {
        return BindingBuilder
                .bind(serviceErrorQueue)
                .to(errorExchange)
                .with(serviceName);
    }

    @Bean
    public MessageRecoverer republishMessageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate, "error.direct", serviceName);
    }
}