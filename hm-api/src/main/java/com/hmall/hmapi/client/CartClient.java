package com.hmall.hmapi.client;

import com.hmall.hmapi.client.fallback.CartClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(value = "cart-service", fallbackFactory = CartClientFallback.class)
public interface CartClient {
    @DeleteMapping("/carts")
    void deleteCartItemByIds(@RequestParam("ids") List<Long> ids);
}
