package com.hmall.trade.listener;

import com.hmall.hmapi.client.PayClient;
import com.hmall.hmapi.dto.PayOrderDTO;
import com.hmall.trade.constants.MqConstants;
import com.hmall.trade.domain.po.Order;
import com.hmall.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PayStatusListener {

    @Autowired
    private final IOrderService orderService;
    @Autowired
    private final PayClient payClient;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(
                            name = "trade.pay.success",
                            durable = "true"
                    ),
                    exchange = @Exchange(
                            name = "pay.direct",
                            type = ExchangeTypes.DIRECT
                    ),
                    key = "pay.success"
            )
    )
    public void listenPaySuccess(Long orderId) {
        log.info("收到支付成功消息，订单ID：{}", orderId);
        orderService.markOrderPaySuccess(orderId);
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(
                            name = MqConstants.DELAY_ORDER_QUEUE_NAME,
                            durable = "true"
                    ),
                    exchange = @Exchange(
                            name = MqConstants.ORDER_EXCHANGE_NAME,
                            delayed = "true"
                    ),
                    key = MqConstants.DELAY_ORDER_KEY
            )
    )
    public void listenOrderDelayMessage(Long orderId) {
        log.info("收到订单延迟消息，订单ID：{}", orderId);
        Order order = orderService.getById(orderId);
        if (order == null || order.getStatus() != 1) {
            return;
        }
        PayOrderDTO payOrderDTO = payClient.queryPayOrderByBizOrderNo(orderId);
        if (payOrderDTO != null && payOrderDTO.getStatus() == 3) {
            orderService.markOrderPaySuccess(orderId);
        } else {
            orderService.cancelOrder(orderId);
        }
    }
}
