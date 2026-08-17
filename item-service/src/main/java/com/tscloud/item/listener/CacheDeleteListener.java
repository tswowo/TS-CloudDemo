package com.tscloud.item.listener;

import com.tscloud.item.constants.MqConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 缓存删除补偿消费者：deleteItemCache 删除失败时发送延迟消息，由本监听器重试删除。
 * 重试仍失败则抛异常，走 Spring AMQP 重试机制，最终进入 error.direct 死信队列（按服务名绑定 item-service.error.queue）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheDeleteListener {

    private final StringRedisTemplate redisTemplate;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(
                            name = MqConstants.CACHE_DELETE_QUEUE,
                            durable = "true"
                    ),
                    exchange = @Exchange(
                            name = MqConstants.CACHE_DELETE_EXCHANGE,
                            type = ExchangeTypes.DIRECT,
                            delayed = "true"
                    ),
                    key = MqConstants.CACHE_DELETE_KEY
            )
    )
    public void listenCacheDelete(Long itemId) {
        log.info("收到缓存删除重试消息，商品ID：{}", itemId);
        try {
            redisTemplate.delete("item:id:" + itemId);
        } catch (Exception e) {
            // 抛出异常触发 Spring AMQP 重试，重试耗尽后进入死信队列，人工介入
            log.error("重试删除商品缓存失败, itemId={}", itemId, e);
            throw new RuntimeException("删除商品缓存失败, itemId=" + itemId, e);
        }
    }
}
