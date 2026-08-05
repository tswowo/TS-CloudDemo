package com.hmall.hmapi.client.fallback;

import com.hmall.common.exception.BizIllegalException;
import com.hmall.hmapi.client.SearchClient;
import com.hmall.hmapi.dto.ItemDoc;
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
