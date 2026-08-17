package com.tscloud.trade;

import com.tscloud.api.client.CartClient;
import com.tscloud.api.client.ItemClient;
import com.tscloud.api.config.DefaultFeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@MapperScan("com.tscloud.trade.mapper")
@EnableFeignClients(basePackageClasses = {CartClient.class, ItemClient.class},defaultConfiguration = DefaultFeignConfig.class)
@SpringBootApplication
public class TradeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeServiceApplication.class, args);
    }

}
