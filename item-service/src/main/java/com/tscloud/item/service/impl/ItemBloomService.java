package com.tscloud.item.service.impl;

import com.tscloud.item.domain.po.Item;
import com.tscloud.item.mapper.ItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 商品布隆过滤器（双桶交替重建）：布隆过滤器不支持删除元素，商品下架后
 * 无法从过滤器中移除，因此用 active/standby 两个桶，定时任务重建 standby
 * （全量加载商品 id）后原子切换 active，再删除旧桶。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemBloomService {

    private static final String BLOOM_ACTIVE = "bloom:item:active";
    private static final String BLOOM_STANDBY = "bloom:item:standby";
    /** 预期插入量：商品 id 数量级上限 */
    private static final long EXPECTED_INSERTIONS = 10000L;
    /** 误判率：1% 的误判最多多查一次库，可接受 */
    private static final double FALSE_PROBABILITY = 0.01;

    private final RedissonClient redissonClient;
    private final ItemMapper itemMapper;

    /** 当前活跃桶的 key，重建时切换；单实例部署用本地变量，多实例需存 Redis 保证一致 */
    private volatile String activeKey = BLOOM_ACTIVE;
    /** 初始化完成标志：未完成时不拦截查询，降级为直接查库 */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        try {
            RBloomFilter<Long> active = redissonClient.getBloomFilter(BLOOM_ACTIVE);
            if (!active.isExists()) {
                active.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
                loadAll(active);
            }
            RBloomFilter<Long> standby = redissonClient.getBloomFilter(BLOOM_STANDBY);
            if (!standby.isExists()) {
                standby.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
            }
            activeKey = BLOOM_ACTIVE;
            initialized.set(true);
            log.info("商品布隆过滤器初始化完成，活跃桶={}", activeKey);
        } catch (Exception e) {
            // 初始化失败不阻塞启动：查询降级为直查数据库，重建定时任务会重试
            log.error("商品布隆过滤器初始化失败，缓存穿透防护降级为直查数据库", e);
        }
    }

    /** 判断商品 id 是否可能存在：未初始化时放行（返回 true 降级为查库） */
    public boolean mayContain(Long id) {
        if (!initialized.get()) {
            return true;
        }
        return getBloom(activeKey).contains(id);
    }

    /** 新商品加入活跃桶；定时重建时的全量加载会覆盖新增记录 */
    public void add(Long id) {
        if (initialized.get()) {
            getBloom(activeKey).add(id);
        }
    }

    /** 重建非活跃桶并原子切换，解决布隆过滤器不能删除元素的问题 */
    public void rebuild() {
        try {
            String targetKey = activeKey.equals(BLOOM_ACTIVE) ? BLOOM_STANDBY : BLOOM_ACTIVE;
            RBloomFilter<Long> target = redissonClient.getBloomFilter(targetKey);
            target.delete();
            target.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
            loadAll(target);
            String oldKey = activeKey;
            activeKey = targetKey;
            redissonClient.getKeys().delete(oldKey);
            initialized.set(true);
            log.info("商品布隆过滤器重建完成，活跃桶切换为 {}", activeKey);
        } catch (Exception e) {
            // 重建失败不切换：旧桶继续服务，下次定时任务重试
            log.error("商品布隆过滤器重建失败，保持当前活跃桶", e);
        }
    }

    private RBloomFilter<Long> getBloom(String key) {
        return redissonClient.getBloomFilter(key);
    }

    private void loadAll(RBloomFilter<Long> filter) {
        for (Item item : itemMapper.selectList(null)) {
            filter.add(item.getId());
        }
    }
}
