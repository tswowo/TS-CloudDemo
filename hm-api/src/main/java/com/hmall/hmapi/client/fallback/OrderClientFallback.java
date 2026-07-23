package com.hmall.hmapi.client.fallback;

import com.hmall.common.exception.BizIllegalException;
import com.hmall.hmapi.client.OrderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

@Slf4j
public class OrderClientFallback implements FallbackFactory<OrderClient> {

    @Override
    public OrderClient create(Throwable cause) {
        return new OrderClient() {
            @Override
            public void markOrderPaySuccess(Long orderId) {
                log.error("远程调用OrderClient#markOrderPaySuccess方法出现异常，参数：[orderId={}]", orderId, cause);
                throw new BizIllegalException(cause);
            }
        };
    }
}
