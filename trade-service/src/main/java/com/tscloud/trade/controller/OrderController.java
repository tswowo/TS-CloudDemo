package com.tscloud.trade.controller;

import com.tscloud.common.utils.BeanUtils;
import com.tscloud.common.domain.PageDTO;
import com.tscloud.common.domain.PageQuery;
import com.tscloud.trade.domain.dto.OrderFormDTO;
import com.tscloud.trade.domain.vo.OrderVO;
import com.tscloud.trade.service.IOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Param;
import org.springframework.web.bind.annotation.*;

@Api(tags = "订单管理接口")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final IOrderService orderService;

    @ApiOperation("根据id查询订单")
    @GetMapping("{id}")
    public OrderVO queryOrderById(@Param ("订单id")@PathVariable("id") Long orderId) {
        return BeanUtils.copyBean(orderService.getById(orderId), OrderVO.class);
    }

    @ApiOperation("分页查询订单（商户端，可按状态筛选）")
    @GetMapping("/page")
    public PageDTO<OrderVO> queryOrderByPage(PageQuery query,
                                             @RequestParam(value = "status", required = false) Integer status) {
        return orderService.queryOrderByPage(query, status);
    }

    @ApiOperation("创建订单")
    @PostMapping
    public Long createOrder(@RequestBody OrderFormDTO orderFormDTO){
        return orderService.createOrder(orderFormDTO);
    }

    @ApiOperation("标记订单已支付")
    @ApiImplicitParam(name = "orderId", value = "订单id", paramType = "path")
    @PutMapping("/{orderId}")
    public void markOrderPaySuccess(@PathVariable("orderId") Long orderId) {
        orderService.markOrderPaySuccess(orderId);
    }
}
