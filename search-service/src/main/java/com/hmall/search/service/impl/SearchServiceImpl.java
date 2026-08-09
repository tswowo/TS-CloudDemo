package com.hmall.search.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.common.domain.PageDTO;
import com.hmall.search.domain.dto.ItemDTO;
import com.hmall.search.domain.dto.ItemDoc;
import com.hmall.search.domain.po.Item;
import com.hmall.search.domain.query.ItemPageQuery;
import com.hmall.search.mapper.ItemMapper;
import com.hmall.search.service.ISearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    @Override
    public PageDTO<ItemDTO> searchFromEsByCondition(ItemPageQuery query) {
        log.debug("开始对索引库分页查询,查询参数:{}", query);
        int pageNo = query.getPageNo() < 1 ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() < 1 ? 10 : query.getPageSize();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        SearchRequest request = new SearchRequest("items");
        request.source().from((pageNo - 1) * pageSize);
        request.source().size(pageSize);
        BoolQueryBuilder boolQuery = buildBoolQuery(query);
        if (StringUtils.isNotBlank(query.getKey())) {
            request.source()
                    .highlighter(
                            SearchSourceBuilder.highlight()
                                    .field("name")
                                    .preTags("<em>")
                                    .postTags("</em>")
                    );
        }
        FunctionScoreQueryBuilder functionScoreQuery = QueryBuilders.functionScoreQuery(
                boolQuery,
                new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                        new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                                QueryBuilders.termQuery("isAD", true),
                                ScoreFunctionBuilders.weightFactorFunction(100)
                        )
                }
        );
        request.source().query(functionScoreQuery);
        if (StringUtils.isNotBlank(query.getSortBy())) {
            request.source().sort(query.getSortBy(), query.getIsAsc() ? SortOrder.ASC : SortOrder.DESC);
        }

        SearchResponse response;
        try {
            response = client.search(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.warn("索引库分页查询异常,失败query:{}", query);
            return new PageDTO<>(0L, 0L, List.of());
        }
        return parseResponse(response, pageSize);
    }

    @Override
    public Map<String, List<String>> getFilters(ItemPageQuery query) {
        log.debug("开始获取索引库聚合过滤条件,查询参数:{}", query);
        SearchRequest request = new SearchRequest("items");
        request.source().query(buildBoolQuery(query));
        request.source().size(0);
        List<String> brand = getAggregation(request, "brand");
        List<String> category = getAggregation(request, "category");
        return Map.of("brand", brand, "category", category);
    }

    private BoolQueryBuilder buildBoolQuery(ItemPageQuery query) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        if (StringUtils.isNotBlank(query.getKey())) {
            boolQuery.must(QueryBuilders.matchQuery("name", query.getKey()));
        }
        if (StringUtils.isNotBlank(query.getCategory())) {
            boolQuery.filter(QueryBuilders.termQuery("category", query.getCategory()));
        }
        if (StringUtils.isNotBlank(query.getBrand())) {
            boolQuery.filter(QueryBuilders.termQuery("brand", query.getBrand()));
        }
        if (query.getMaxPrice() != null || query.getMinPrice() != null) {
            boolQuery.filter(QueryBuilders.rangeQuery("price")
                    .lte(query.getMaxPrice())
                    .gte(query.getMinPrice()));
        }
        return boolQuery;
    }

    private List<String> getAggregation(SearchRequest request, String target) {
        request.source().aggregation(
                AggregationBuilders.terms(target + "_agg")
                        .field(target)
                        .size(10)
        );
        SearchResponse response;
        try {
            response = client.search(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Aggregations aggregations = response.getAggregations();
        Terms terms = aggregations.get(target + "_agg");
        List<? extends Terms.Bucket> buckets = terms.getBuckets();
        List<String> resultList = new ArrayList<>(buckets.size());
        for (Terms.Bucket bucket : buckets) {
            resultList.add(bucket.getKeyAsString());
        }
        return resultList;
    }


    private static PageDTO<ItemDTO> parseResponse(SearchResponse response, int pageSize) {
        SearchHits hits = response.getHits();
        long total = hits.getTotalHits().value;
        long pages = (total + pageSize - 1) / pageSize;
        log.info("总条数:{}, 总页数:{}", total, pages);
        SearchHit[] searchHits = hits.getHits();
        List<ItemDTO> itemDTOS = new ArrayList<>(searchHits.length);
        for (SearchHit hit : searchHits) {
            String source = hit.getSourceAsString();
            ItemDoc itemDoc = JSONUtil.toBean(source, ItemDoc.class);
            Map<String, HighlightField> highlightFields = hit.getHighlightFields();
            if (highlightFields != null && !highlightFields.isEmpty()) {
                HighlightField field = highlightFields.get("name");
                if (field != null && field.getFragments().length > 0) {
                    String highLightName = field.getFragments()[0].toString();
                    itemDoc.setName(highLightName);
                }
            }
            ItemDTO itemDTO = BeanUtil.copyProperties(itemDoc, ItemDTO.class);
            try {
                itemDTO.setId(Long.valueOf(hit.getId()));
            } catch (NumberFormatException e) {
                log.warn("ES文档_id不是数字格式,无法转换为商品id: {}", hit.getId());
            }
            itemDTOS.add(itemDTO);
        }
        return new PageDTO<>(total, pages, itemDTOS);
    }
}