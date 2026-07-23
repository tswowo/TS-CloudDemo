package com.hmall.hmapi.client.fallback;

import com.hmall.common.exception.BizIllegalException;
import com.hmall.hmapi.client.UserClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

@Slf4j
public class UserClientFallback implements FallbackFactory<UserClient> {

    @Override
    public UserClient create(Throwable cause) {
        return new UserClient() {
            @Override
            public void deductMoney(String pw, Integer amount) {
                log.error("远程调用UserClient#deductMoney方法出现异常，参数：[amount={}]", amount, cause);
                throw new BizIllegalException(cause);
            }
        };
    }
}
