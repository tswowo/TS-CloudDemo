package com.hmall.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RabbitMqHelper {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqHelper(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendMessage(String exchange, String routingKey, Object msg) {
        rabbitTemplate.convertAndSend(exchange, routingKey, msg);
    }

    public void sendDelayMessage(String exchange, String routingKey, Object msg, int delay) {
        rabbitTemplate.convertAndSend(exchange, routingKey, msg, message -> {
            message.getMessageProperties().setDelay(delay);
            return message;
        });
    }

    public boolean sendMessageWithConfirm(String exchange, String routingKey, Object msg, int timeoutSeconds) {
        CorrelationData cd = new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.convertAndSend(exchange, routingKey, msg, cd);
        try {
            CorrelationData.Confirm confirm = cd.getFuture().get(timeoutSeconds, TimeUnit.SECONDS);
            if (confirm.isAck()) {
                log.debug("消息发送成功, id: {}", cd.getId());
                return true;
            }
            log.error("消息发送失败, id: {}, reason: {}", cd.getId(), confirm.getReason());
            return false;
        } catch (Exception e) {
            log.error("消息发送确认超时或异常, id: {}", cd.getId(), e);
            return false;
        }
    }
}