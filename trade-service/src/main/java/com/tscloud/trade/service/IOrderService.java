package com.tscloud.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tscloud.common.domain.PageDTO;
import com.tscloud.common.domain.PageQuery;
import com.tscloud.trade.domain.dto.OrderFormDTO;
import com.tscloud.trade.domain.po.Order;
import com.tscloud.trade.domain.vo.OrderVO;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
public interface IOrderService extends IService<Order> {

    Long createOrder(OrderFormDTO orderFormDTO);

    void markOrderPaySuccess(Long orderId);

    void cancelOrder(Long orderId);

    PageDTO<OrderVO> queryOrderByPage(PageQuery query, Integer status);
}
