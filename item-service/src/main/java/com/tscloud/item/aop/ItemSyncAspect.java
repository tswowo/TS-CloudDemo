package com.tscloud.item.aop;

import com.tscloud.common.utils.BeanUtils;
import com.tscloud.common.utils.RabbitMqHelper;
import com.tscloud.item.annotation.ItemSync;
import com.tscloud.item.domain.dto.ItemDoc;
import com.tscloud.item.domain.po.Item;
import com.tscloud.item.enums.Operation;
import com.tscloud.item.service.IItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class ItemSyncAspect {

    private final IItemService itemService;
    private final RabbitTemplate rabbitTemplate;

    @AfterReturning("@annotation(itemSync)")
    public void handleItemSync(JoinPoint joinPoint, ItemSync itemSync) {
        Operation op = itemSync.operation();
        Object[] args = joinPoint.getArgs();

        if (op == Operation.DELETE) {
            RabbitMqHelper mq = new RabbitMqHelper(rabbitTemplate);
            mq.sendMessage("item.direct", "item.delete", args[0]);
            log.debug("发送商品删除MQ消息, id: {}", args[0]);
            return;
        }

        Item entity = (Item) args[0];
        Item savedItem = itemService.getById(entity.getId());
        if (savedItem == null) {
            log.warn("商品不存在, id: {}", entity.getId());
            return;
        }
        ItemDoc itemDoc = BeanUtils.copyBean(savedItem, ItemDoc.class);
        itemDoc.setId(savedItem.getId().toString());

        RabbitMqHelper mq = new RabbitMqHelper(rabbitTemplate);
        mq.sendMessage("item.direct", "item.save", itemDoc);
        log.debug("发送商品同步MQ消息, id: {}", savedItem.getId());
    }
}