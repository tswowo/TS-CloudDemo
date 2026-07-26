package com.hmall.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmall.hmapi.dto.OrderDetailDTO;
import com.hmall.trade.domain.po.Order;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
public interface OrderMapper extends BaseMapper<Order> {

    @Select("select * from order_detail where order_id = #{orderId}")
    List<OrderDetailDTO> queryOrderItemsByOrderId(Long orderId);
}
