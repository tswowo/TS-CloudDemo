package com.tscloud.pay;

import com.tscloud.api.client.OrderClient;
import com.tscloud.api.client.UserClient;
import com.tscloud.api.config.DefaultFeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@MapperScan("com.tscloud.pay.mapper")
@SpringBootApplication
@EnableFeignClients(basePackageClasses = {OrderClient.class, UserClient.class}, defaultConfiguration = DefaultFeignConfig.class)
public class PayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayServiceApplication.class, args);
    }

}
