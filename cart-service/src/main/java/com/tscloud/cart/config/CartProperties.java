package com.tscloud.cart.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@ConfigurationProperties(prefix = "ts.cart")
@Component
public class CartProperties {
    private Integer maxItems;
}
