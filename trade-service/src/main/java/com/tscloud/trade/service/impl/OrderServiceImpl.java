package com.tscloud.trade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tscloud.common.exception.BadRequestException;
import com.tscloud.common.utils.RabbitMqHelper;
import com.tscloud.common.utils.UserContext;
import com.tscloud.api.client.CartClient;
import com.tscloud.api.client.ItemClient;
import com.tscloud.api.dto.ItemDTO;
import com.tscloud.api.dto.OrderDetailDTO;
import com.tscloud.trade.constants.MqConstants;
import com.tscloud.trade.domain.dto.OrderFormDTO;
import com.tscloud.trade.domain.po.Order;
import com.tscloud.trade.domain.po.OrderDetail;
import com.tscloud.trade.mapper.OrderMapper;
import com.tscloud.trade.service.IOrderDetailService;
import com.tscloud.trade.service.IOrderService;
import com.tscloud.common.domain.PageDTO;
import com.tscloud.common.domain.PageQuery;
import com.tscloud.trade.domain.vo.OrderVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private final ItemClient itemClient;
    private final IOrderDetailService detailService;
    private final CartClient cartClient;
    private final RabbitTemplate rabbitTemplate;
    private final OrderMapper orderMapper;

    @Override
    @Transactional
    @GlobalTransactional(timeoutMills = 300000, name = "createOrder")
    public Long createOrder(OrderFormDTO orderFormDTO) {
        // 1.订单数据
        Order order = new Order();
        // 1.1.查询商品
        List<OrderDetailDTO> detailDTOS = orderFormDTO.getDetails();
        // 1.2.获取商品id和数量的Map
        Map<Long, Integer> itemNumMap = detailDTOS.stream()
                .collect(Collectors.toMap(OrderDetailDTO::getItemId, OrderDetailDTO::getNum));
        Set<Long> itemIds = itemNumMap.keySet();
        // 1.3.查询商品
        List<ItemDTO> items = itemClient.queryItemByIds(itemIds);
        if (items == null || items.size() < itemIds.size()) {
            throw new BadRequestException("商品不存在");
        }
        // 1.4.基于商品价格、购买数量计算商品总价：totalFee
        int total = 0;
        for (ItemDTO item : items) {
            total += item.getPrice() * itemNumMap.get(item.getId());
        }
        order.setTotalFee(total);
        // 1.5.其它属性
        order.setPaymentType(orderFormDTO.getPaymentType());
        order.setUserId(UserContext.getUser());
        order.setStatus(1);
        // 1.6.将Order写入数据库order表中
        save(order);

        // 2.保存订单详情
        List<OrderDetail> details = buildDetails(order.getId(), items, itemNumMap);
        detailService.saveBatch(details);

        // 3.清理购物车商品
        List<Long> itemIdsList = new ArrayList<>(itemIds);
        cartClient.deleteCartItemByIds(itemIdsList);

        // 4.扣减库存
        try {
            itemClient.deductStock(detailDTOS);
        } catch (Exception e) {
            throw new RuntimeException("库存不足！");
        }

        //发送延迟消息，校验订单支付状态
        log.info("发送延迟消息，校验订单支付状态");
        RabbitMqHelper mq=new RabbitMqHelper(rabbitTemplate);
        mq.sendDelayMessage(MqConstants.ORDER_EXCHANGE_NAME, MqConstants.DELAY_ORDER_KEY,order.getId(),20000);

        return order.getId();
    }

    @Override
    public void markOrderPaySuccess(Long orderId) {
        Order curOrder = this.getById(orderId);
        if (curOrder == null || curOrder.getStatus() != 1) {
            return;
        }
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(2);
        order.setPayTime(LocalDateTime.now());
        updateById(order);
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId) {
        Order curOrder = this.getById(orderId);
        if (curOrder == null || curOrder.getStatus() != 1) {
            return;
        }
        // 标记订单状态为已关闭
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(5);
        order.setUpdateTime(LocalDateTime.now());
        updateById(order);
        // 恢复扣减的库存
        List<OrderDetailDTO> items = orderMapper.queryOrderItemsByOrderId(orderId);
        itemClient.restoreStock(items);
    }

    @Override
    public PageDTO<OrderVO> queryOrderByPage(PageQuery query, Integer status) {
        // 商户端分页查询订单，可按状态筛选，默认按创建时间倒序
        Page<Order> page = lambdaQuery()
                .eq(status != null, Order::getStatus, status)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        return PageDTO.of(page, OrderVO.class);
    }

    private List<OrderDetail> buildDetails(Long orderId, List<ItemDTO> items, Map<Long, Integer> numMap) {
        List<OrderDetail> details = new ArrayList<>(items.size());
        for (ItemDTO item : items) {
            OrderDetail detail = new OrderDetail();
            detail.setName(item.getName());
            detail.setSpec(item.getSpec());
            detail.setPrice(item.getPrice());
            detail.setNum(numMap.get(item.getId()));
            detail.setItemId(item.getId());
            detail.setImage(item.getImage());
            detail.setOrderId(orderId);
            details.add(detail);
        }
        return details;
    }
}
