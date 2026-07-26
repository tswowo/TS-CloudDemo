package com.hmall.hmapi.client.fallback;

import com.hmall.hmapi.client.PayClient;
import com.hmall.hmapi.dto.PayOrderDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

@Slf4j
public class PayClientFallback implements FallbackFactory<PayClient> {
    @Override
    public PayClient create(Throwable cause) {
        return new PayClient() {
            @Override
            public PayOrderDTO queryPayOrderByBizOrderNo(Long id) {
                log.error("远程调用PayClient#queryPayOrderByBizOrderNo失败, id:{}", id, cause);
                throw new RuntimeException("查询支付单失败", cause);
            }
        };
    }
}