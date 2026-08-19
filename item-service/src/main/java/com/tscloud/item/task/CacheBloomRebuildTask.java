package com.tscloud.item.task;

import com.tscloud.item.service.impl.ItemBloomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天凌晨重建商品布隆过滤器，清除已删除商品的残留位 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheBloomRebuildTask {

    private final ItemBloomService itemBloomService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void rebuildItemBloom() {
        itemBloomService.rebuild();
    }
}
