package com.tscloud.item.constants;

public class MqConstants {

    /** 缓存删除重试：延迟交换机（依赖 rabbitmq_delayed_message_exchange 插件） */
    public final static String CACHE_DELETE_EXCHANGE = "item.delay.direct";

    public final static String CACHE_DELETE_QUEUE = "item.cache.delete.queue";

    public final static String CACHE_DELETE_KEY = "cache.delete";
}
