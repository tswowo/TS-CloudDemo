package com.tscloud.api.client;

import com.tscloud.api.client.fallback.SearchClientFallback;
import com.tscloud.api.dto.ItemDoc;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "search-service", fallbackFactory = SearchClientFallback.class)
public interface SearchClient {

    @GetMapping("/search/{id}")
    ItemDoc searchById(@PathVariable Long id);

}
