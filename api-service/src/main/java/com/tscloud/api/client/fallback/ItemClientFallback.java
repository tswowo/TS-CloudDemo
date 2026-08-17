package com.tscloud.api.client.fallback;

import com.tscloud.common.exception.BizIllegalException;
import com.tscloud.common.utils.CollUtils;
import com.tscloud.api.client.ItemClient;
import com.tscloud.api.dto.ItemDTO;
import com.tscloud.api.dto.OrderDetailDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.Collection;
import java.util.List;

@Slf4j
public class ItemClientFallback implements FallbackFactory<ItemClient> {

    @Override
    public ItemClient create(Throwable cause) {
        return new ItemClient() {
            @Override
            public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
                log.error("远程调用ItemClient#queryItemByIds方法出现异常，参数：{}", ids, cause);
                return CollUtils.emptyList();
            }

            @Override
            public void deductStock(List<OrderDetailDTO> items) {
                throw new BizIllegalException(cause);
            }

            @Override
            public void restoreStock(List<OrderDetailDTO> items) {
                throw new BizIllegalException(cause);
            }

            @Override
            public ItemDTO queryItemById(Long id) {
                throw new BizIllegalException(cause);
            }
        };
    }
}
