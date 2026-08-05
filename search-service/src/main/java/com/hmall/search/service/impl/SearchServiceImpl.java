package com.hmall.search.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.search.domain.dto.ItemDoc;
import com.hmall.search.domain.po.Item;
import com.hmall.search.mapper.ItemMapper;
import com.hmall.search.service.ISearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchServiceImpl extends ServiceImpl<ItemMapper, Item> implements ISearchService {

    final private RestHighLevelClient client;

    @Override

    public ItemDoc queryItemDocById(Long id) {
        GetRequest request = new GetRequest("items", id.toString());
        GetResponse response;
        try {
            response = client.get(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.warn("索引库根据id查询异常,失败id:{}", id);
            return null;
        }
        if (!response.isExists()) {
            log.warn("索引库根据id查询结果为空,失败id:{}", id);
            return null;
        }

        String json = response.getSourceAsString();
        ItemDoc itemDoc = JSONUtil.toBean(json, ItemDoc.class);
        log.info("索引库根据id查询成功,成功id:{},查询结果:{}", id, itemDoc);
        return itemDoc;
    }
}