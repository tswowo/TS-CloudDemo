package com.hmall.item.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.CollUtils;
import com.hmall.common.utils.RabbitMqHelper;
import com.hmall.common.utils.RedisLockHelper;
import com.hmall.item.annotation.ItemSync;
import com.hmall.item.constants.MqConstants;
import com.hmall.item.domain.dto.ItemDTO;
import com.hmall.item.domain.dto.OrderDetailDTO;
import com.hmall.item.domain.po.Item;
import com.hmall.item.enums.Operation;
import com.hmall.item.mapper.ItemMapper;
import com.hmall.item.service.IItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 商品表 服务实现类
 * </p>
 *
 * @author 虎哥
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemServiceImpl extends ServiceImpl<ItemMapper, Item> implements IItemService {

    private final StringRedisTemplate redisTemplate;
    private final RedisLockHelper redisLockHelper;
    private final RabbitTemplate rabbitTemplate;
    private final ItemStockService itemStockService;

    /** 商品缓存 key 前缀 */
    private static final String ITEM_CACHE_PREFIX = "item:id:";
    /** 库存锁 key 前缀 */
    private static final String STOCK_LOCK_PREFIX = "lock:stock:";
    /** 缓存基础有效期 30 分钟，实际 TTL = 基础 + 随机 0~5 分钟（错开过期时间，防缓存雪崩） */
    private static final long ITEM_CACHE_TTL_SECONDS = 30 * 60;
    /** 库存锁过期时间：持锁方宕机时靠它自动释放，防止死锁 */
    private static final long STOCK_LOCK_EXPIRE_SECONDS = 10;

    @Override
    @ItemSync(operation = Operation.SAVE)
    public boolean save(Item entity) {
        boolean success = super.save(entity);
        if (success) {
            deleteItemCache(entity.getId());
        }
        return success;
    }

    @Override
    @ItemSync(operation = Operation.UPDATE)
    public boolean updateById(Item entity) {
        boolean success = super.updateById(entity);
        if (success) {
            deleteItemCache(entity.getId());
        }
        return success;
    }

    @Override
    @ItemSync(operation = Operation.DELETE)
    public boolean removeById(Serializable id) {
        boolean success = super.removeById(id);
        if (success) {
            deleteItemCache((Long) id);
        }
        return success;
    }

    @Override
    public void deductStock(List<OrderDetailDTO> items) {
        // 合并相同商品的明细并排序：同一商品只加一次锁，且所有请求加锁顺序一致，避免死锁
        List<OrderDetailDTO> merged = mergeAndSort(items);
        String lockValue = UUID.randomUUID().toString();
        List<Long> lockedIds = new ArrayList<>();
        try {
            for (OrderDetailDTO item : merged) {
                String lockKey = STOCK_LOCK_PREFIX + item.getItemId();
                if (!redisLockHelper.tryLock(lockKey, lockValue, STOCK_LOCK_EXPIRE_SECONDS)) {
                    throw new BizIllegalException("系统繁忙，请稍后再试！");
                }
                lockedIds.add(item.getItemId());
            }
            // 持锁后预校验库存：任一商品不足，在扣减发生前直接失败，不会产生部分扣减
            checkStock(merged);
            // 锁包事务：独立 Bean 的事务方法任一条失败整体回滚；事务提交后本方法 finally 才释放锁
            itemStockService.deductWithTx(merged);
            // 库存已变化，删除商品缓存，防止读到旧库存
            lockedIds.forEach(this::deleteItemCache);
        } finally {
            // 逆序释放，保证持锁期间后续请求按序竞争
            for (int i = lockedIds.size() - 1; i >= 0; i--) {
                redisLockHelper.unlock(STOCK_LOCK_PREFIX + lockedIds.get(i), lockValue);
            }
        }
    }

    @Override
    public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
        if (CollUtils.isEmpty(ids)) {
            return CollUtils.emptyList();
        }
        List<Long> idList = new ArrayList<>(ids);
        // Redis 故障时降级为直接查库，缓存层异常不影响主流程
        try {
            return queryWithCache(idList);
        } catch (Exception e) {
            log.error("商品缓存查询异常，降级为数据库查询, ids={}", idList, e);
            return queryFromDb(idList);
        }
    }

    @Override
    public ItemDTO queryItemById(Long id) {
        try {
            String key = ITEM_CACHE_PREFIX + id;
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return JSONUtil.toBean(json, ItemDTO.class);
            }
            // 缓存未命中：互斥锁防击穿，只有一个请求回源查库建缓存，其余等锁后重读
            String lockKey = "lock:cache:" + id;
            String lockValue = UUID.randomUUID().toString();
            boolean locked = false;
            try {
                if (redisLockHelper.tryLock(lockKey, lockValue, 10)) {
                    locked = true;
                    // 双重检查：拿到锁后缓存可能已被上一个持锁者重建
                    json = redisTemplate.opsForValue().get(key);
                    if (json != null) {
                        return JSONUtil.toBean(json, ItemDTO.class);
                    }
                    Item item = getById(id);
                    if (item == null) {
                        return null;
                    }
                    ItemDTO dto = BeanUtils.copyBean(item, ItemDTO.class);
                    redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(dto), randomTtlSeconds(), TimeUnit.SECONDS);
                    return dto;
                }
                // 未拿到锁：短暂等待后重读缓存，避免大量请求同时落库
                Thread.sleep(50);
                json = redisTemplate.opsForValue().get(key);
                if (json != null) {
                    return JSONUtil.toBean(json, ItemDTO.class);
                }
                // 兜底：仍未命中则直接查库返回，不建缓存（建缓存统一走持锁路径）
                Item item = getById(id);
                return item == null ? null : BeanUtils.copyBean(item, ItemDTO.class);
            } finally {
                if (locked) {
                    redisLockHelper.unlock(lockKey, lockValue);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Item item = getById(id);
            return item == null ? null : BeanUtils.copyBean(item, ItemDTO.class);
        } catch (Exception e) {
            log.error("商品缓存查询异常，降级为数据库查询, id={}", id, e);
            Item item = getById(id);
            return item == null ? null : BeanUtils.copyBean(item, ItemDTO.class);
        }
    }

    @Override
    public void setStock(Long id, Integer stock) {
        if (stock == null || stock < 0) {
            throw new BadRequestException("库存不能为空或负数");
        }
        String lockKey = STOCK_LOCK_PREFIX + id;
        String lockValue = UUID.randomUUID().toString();
        try {
            // 与扣减/恢复共用同一把商品锁，防止"商户设置库存"与"用户扣减"并发时丢失更新
            if (!redisLockHelper.tryLock(lockKey, lockValue, STOCK_LOCK_EXPIRE_SECONDS)) {
                throw new BizIllegalException("系统繁忙，请稍后再试！");
            }
            baseMapper.setStock(id, stock);
            deleteItemCache(id);
        } finally {
            redisLockHelper.unlock(lockKey, lockValue);
        }
    }

    @Override
    public void restoreStock(List<OrderDetailDTO> items) {
        List<OrderDetailDTO> merged = mergeAndSort(items);
        String lockValue = UUID.randomUUID().toString();
        List<Long> lockedIds = new ArrayList<>();
        try {
            for (OrderDetailDTO item : merged) {
                String lockKey = STOCK_LOCK_PREFIX + item.getItemId();
                if (!redisLockHelper.tryLock(lockKey, lockValue, STOCK_LOCK_EXPIRE_SECONDS)) {
                    throw new BizIllegalException("系统繁忙，请稍后再试！");
                }
                lockedIds.add(item.getItemId());
            }
            // 锁包事务：恢复走独立 Bean 的事务方法
            itemStockService.restoreWithTx(merged);
            // 库存已变化，删除商品缓存
            lockedIds.forEach(this::deleteItemCache);
        } finally {
            for (int i = lockedIds.size() - 1; i >= 0; i--) {
                redisLockHelper.unlock(STOCK_LOCK_PREFIX + lockedIds.get(i), lockValue);
            }
        }
    }

    /** Cache Aside 读路径：批量 mget 缓存，未命中的 id 回源数据库并回填 */
    private List<ItemDTO> queryWithCache(List<Long> idList) {
        List<String> keys = new ArrayList<>(idList.size());
        for (Long id : idList) {
            keys.add(ITEM_CACHE_PREFIX + id);
        }
        List<String> cachedJsons = redisTemplate.opsForValue().multiGet(keys);
        Map<Long, ItemDTO> resultMap = new HashMap<>();
        List<Long> missIds = new ArrayList<>();
        for (int i = 0; i < idList.size(); i++) {
            String json = cachedJsons == null ? null : cachedJsons.get(i);
            if (json != null) {
                resultMap.put(idList.get(i), JSONUtil.toBean(json, ItemDTO.class));
            } else {
                missIds.add(idList.get(i));
            }
        }
        if (!missIds.isEmpty()) {
            List<Item> dbItems = listByIds(missIds);
            for (Item item : dbItems) {
                ItemDTO dto = BeanUtils.copyBean(item, ItemDTO.class);
                resultMap.put(item.getId(), dto);
                redisTemplate.opsForValue().set(ITEM_CACHE_PREFIX + item.getId(),
                        JSONUtil.toJsonStr(dto), randomTtlSeconds(), TimeUnit.SECONDS);
            }
        }
        // 按请求 id 顺序返回
        List<ItemDTO> result = new ArrayList<>(idList.size());
        for (Long id : idList) {
            ItemDTO dto = resultMap.get(id);
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    private List<ItemDTO> queryFromDb(List<Long> idList) {
        return BeanUtils.copyList(listByIds(idList), ItemDTO.class);
    }

    /** 合并相同商品的明细（num 相加），并按 itemId 升序排列 */
    private List<OrderDetailDTO> mergeAndSort(List<OrderDetailDTO> items) {
        Map<Long, Integer> numMap = new LinkedHashMap<>();
        for (OrderDetailDTO item : items) {
            numMap.merge(item.getItemId(), item.getNum(), Integer::sum);
        }
        return numMap.entrySet().stream()
                .map(e -> new OrderDetailDTO().setItemId(e.getKey()).setNum(e.getValue()))
                .sorted(Comparator.comparing(OrderDetailDTO::getItemId))
                .collect(Collectors.toList());
    }

    /** 预校验库存：在扣减发生前拦截库存不足，保证不会出现部分扣减 */
    private void checkStock(List<OrderDetailDTO> items) {
        List<Long> ids = items.stream().map(OrderDetailDTO::getItemId).collect(Collectors.toList());
        Map<Long, Integer> stockMap = listByIds(ids).stream()
                .collect(Collectors.toMap(Item::getId, Item::getStock));
        for (OrderDetailDTO item : items) {
            Integer stock = stockMap.get(item.getItemId());
            if (stock == null || stock < item.getNum()) {
                throw new BizIllegalException("库存不足！");
            }
        }
    }

    /** Cache Aside 写路径：先更新数据库，再删除缓存；删除失败发延迟消息重试，重试仍失败进死信队列 */
    private void deleteItemCache(Long id) {
        try {
            redisTemplate.delete(ITEM_CACHE_PREFIX + id);
        } catch (Exception e) {
            log.error("删除商品缓存失败，发送延迟消息重试, id={}", id, e);
            try {
                new RabbitMqHelper(rabbitTemplate)
                        .sendDelayMessage(MqConstants.CACHE_DELETE_EXCHANGE, MqConstants.CACHE_DELETE_KEY, id, 1000);
            } catch (Exception ex) {
                log.error("缓存删除重试消息发送失败, id={}", id, ex);
            }
        }
    }

    /** 30 分钟 + 随机 0~5 分钟，避免同一批缓存同时过期 */
    private long randomTtlSeconds() {
        return ITEM_CACHE_TTL_SECONDS + ThreadLocalRandom.current().nextInt(5 * 60);
    }
}
