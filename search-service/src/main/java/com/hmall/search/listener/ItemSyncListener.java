package com.hmall.search.listener;

import cn.hutool.json.JSONUtil;
import com.hmall.search.domain.dto.ItemDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class ItemSyncListener {

    private final RestHighLevelClient client;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = "search.item.sync", durable = "true"),
                    exchange = @Exchange(name = "item.direct", type = ExchangeTypes.DIRECT),
                    key = "item.save"
            )
    )
    public void handleItemSaveOrUpdate(ItemDoc itemDoc) {
        try {
            IndexRequest request = new IndexRequest("items")
                    .id(itemDoc.getId())
                    .source(JSONUtil.toJsonStr(itemDoc), XContentType.JSON);
            client.index(request, RequestOptions.DEFAULT);
            log.info("索引库同步成功, id: {}", itemDoc.getId());
        } catch (IOException e) {
            log.error("索引库同步失败, id: {}", itemDoc.getId(), e);
            throw new RuntimeException(e);
        }
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = "search.item.delete", durable = "true"),
                    exchange = @Exchange(name = "item.direct", type = ExchangeTypes.DIRECT),
                    key = "item.delete"
            )
    )
    public void handleItemDelete(Long itemId) {
        try {
            DeleteRequest request = new DeleteRequest("items", itemId.toString());
            client.delete(request, RequestOptions.DEFAULT);
            log.info("索引库删除成功, id: {}", itemId);
        } catch (IOException e) {
            log.error("索引库删除失败, id: {}", itemId, e);
            throw new RuntimeException(e);
        }
    }
}