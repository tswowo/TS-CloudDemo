package com.hmall.hmapi.config;

import com.hmall.common.utils.UserContext;
import com.hmall.hmapi.client.fallback.ItemClientFallback;
import feign.Logger;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class DefaultFeignConfig {
    @Bean
    public Logger.Level defaultFeignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public RequestInterceptor userInfoInterceptor() {
        return requestTemplate -> {
            Long userId = UserContext.getUser();
            if (userId != null) {
                requestTemplate.header(UserContext.USER_HEADER, userId.toString());
            }
        };
    }

    @Bean
    public ItemClientFallback itemClientFallback() {
        return new ItemClientFallback();
    }
}
