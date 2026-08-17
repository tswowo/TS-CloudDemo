package com.tscloud.api.client.fallback;

import com.tscloud.common.exception.BizIllegalException;
import com.tscloud.api.client.SearchClient;
import com.tscloud.api.dto.ItemDoc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;


@Slf4j
public class SearchClientFallback implements FallbackFactory<SearchClient> {
    @Override
    public SearchClient create(Throwable cause) {
        return new SearchClient() {
            @Override
            public ItemDoc searchById(Long id) {
                log.error("远程调用SearchClient#searchById方法出现异常，参数：{}", id, cause);
                throw new BizIllegalException(cause);
            }
        };
    }
}
