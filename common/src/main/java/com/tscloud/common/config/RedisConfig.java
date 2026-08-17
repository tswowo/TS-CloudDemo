package com.tscloud.common.config;

import com.tscloud.common.utils.RedisLockHelper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 相关 Bean 装配。@ConditionalOnClass 保证未引入 Redis starter 的模块不受影响。
 */
@Configuration
@ConditionalOnClass(StringRedisTemplate.class)
public class RedisConfig {

    @Bean
    public RedisLockHelper redisLockHelper(StringRedisTemplate redisTemplate) {
        return new RedisLockHelper(redisTemplate);
    }
}
