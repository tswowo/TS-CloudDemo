package com.tscloud.item;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tscloud.item.domain.dto.ItemDoc;
import com.tscloud.item.domain.po.Item;
import com.tscloud.item.service.impl.ItemServiceImpl;
import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

@SpringBootTest(properties = "spring.profiles.active=local")
class EsDocTest {

    private RestHighLevelClient client;
    @Autowired
    private ItemServiceImpl itemService;

    @Test
    void testDeleteDocument() throws IOException {
        DeleteRequest request = new DeleteRequest("items", "1");
        client.delete(request, RequestOptions.DEFAULT);
    }

    @Test
    void testBulkDocument() throws IOException {
        int pageNo = 1, pageSize = 500;
        while (true) {
            Page<Item> page = itemService.lambdaQuery()
                    .eq(Item::getStatus, 1)
                    .page(Page.of(pageNo, pageSize));
            List<Item> itemDTOList = page.getRecords();
            if (itemDTOList == null || itemDTOList.isEmpty()) {
                break;
            }
            BulkRequest request = new BulkRequest();
            for (Item item : itemDTOList) {
                ItemDoc itemDoc = new ItemDoc();
                BeanUtils.copyProperties(item, itemDoc);
                itemDoc.setId(item.getId().toString());
                request.add(new IndexRequest("items")
                        .id(itemDoc.getId())
                        .source(JSONUtil.toJsonStr(itemDoc), XContentType.JSON));
            }
            client.bulk(request, RequestOptions.DEFAULT);
            pageNo++;
        }

    }

    @Test
    void testGetDocument() throws IOException {
        GetRequest request = new GetRequest("items", "1");
        GetResponse response = client.get(request, RequestOptions.DEFAULT);

        String json = response.getSourceAsString();
        ItemDoc itemDoc = JSONUtil.toBean(json, ItemDoc.class);
        System.out.println(itemDoc);
    }

    @Test
    void testIndexDocument() throws IOException {
        Item item = itemService.getById(317578);
        ItemDoc itemDoc = new ItemDoc();
        BeanUtils.copyProperties(item, itemDoc);

        String jsonStr = JSONUtil.toJsonStr(itemDoc);
        System.out.println(jsonStr);

        IndexRequest request = new IndexRequest("items").id("1");
        request.source(jsonStr, XContentType.JSON);
        client.index(request, RequestOptions.DEFAULT);
    }

    @Test
    void testUpdateDocument() throws IOException {
        String updateInfo = "{\n" +
                "  \"price\": 1145141,\n" +
                "  \"category\": null\n" +
                "}";

        UpdateRequest request = new UpdateRequest("items", "1");
        request.doc(updateInfo, XContentType.JSON);
        request.doc("price", 1145141);
        client.update(request, RequestOptions.DEFAULT);
    }

    @BeforeEach
    void setUp() {
        client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create("http://localhost:19200")
        ));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) {
            client.close();
        }
    }

}
