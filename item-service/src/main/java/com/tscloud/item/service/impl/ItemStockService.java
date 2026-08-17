package com.tscloud.item.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tscloud.common.exception.BizIllegalException;
import com.tscloud.item.domain.dto.OrderDetailDTO;
import com.tscloud.item.domain.po.Item;
import com.tscloud.item.mapper.ItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 库存扣减/恢复的事务执行器。
 * 独立 Bean 的原因：@Transactional 依赖代理生效，若放在 ItemServiceImpl 内部由 this 调用，
 * 自调用不走代理，事务会静默失效。
 * 与分布式锁的组合是「锁包事务」：外层方法（ItemServiceImpl）加锁，
 * 本类事务方法返回时事务已提交，外层 finally 才释放锁——事务提交先于锁释放，无竞态窗口。
 */
@Service
public class ItemStockService extends ServiceImpl<ItemMapper, Item> {

    @Transactional
    public void deductWithTx(List<OrderDetailDTO> items) {
        for (OrderDetailDTO item : items) {
            // SQL 带 stock >= num 条件：受影响行数为 0 说明库存不足，抛异常触发整体回滚
            int affected = baseMapper.updateStock(item);
            if (affected == 0) {
                throw new BizIllegalException("库存不足！");
            }
        }
    }

    @Transactional
    public void restoreWithTx(List<OrderDetailDTO> items) {
        for (OrderDetailDTO item : items) {
            baseMapper.restoreStock(item);
        }
    }
}
