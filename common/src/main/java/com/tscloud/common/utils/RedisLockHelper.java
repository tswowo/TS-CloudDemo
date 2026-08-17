package com.tscloud.common.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的分布式锁工具。
 * 加锁：SET key value NX EX expire —— 一条命令完成「占锁 + 过期时间」，原子性由 Redis 单线程保证；
 * 解锁：Lua 脚本先比对 value 再删除 —— 「判断锁归属 + 删除」两步在服务端原子执行，防止误删其他线程的锁。
 */
public class RedisLockHelper {

    private final StringRedisTemplate redisTemplate;

    /** 解锁脚本：仅当 value 与持锁标识一致时才删除，返回 1 表示解锁成功 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    public RedisLockHelper(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试加锁。
     *
     * @param key           锁 key
     * @param value         持锁标识（调用方传唯一值，如 UUID，解锁时用于校验归属）
     * @param expireSeconds 过期时间，防止持锁方宕机导致死锁
     * @return true 加锁成功
     */
    public boolean tryLock(String key, String value, long expireSeconds) {
        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(key, value, expireSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 释放锁。value 不匹配时不会删除，避免锁过期后误删他人刚获取的锁。
     */
    public void unlock(String key, String value) {
        redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), value);
    }
}
