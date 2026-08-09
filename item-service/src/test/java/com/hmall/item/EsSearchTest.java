package com.hmall.item;

import cn.hutool.json.JSONUtil;
import com.hmall.item.domain.dto.ItemDoc;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

//@SpringBootTest(properties = "spring.profiles.active=local")
class EsSearchTest {

    private RestHighLevelClient client;

    @Test
    void testAggregationQuery() throws IOException {
        SearchRequest request = new SearchRequest("items");

        request.source().size(0);
        request.source().aggregation(
                AggregationBuilders.terms("brand_agg")
                        .field("brand.keyword")
                        .size(10)
        );

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        Aggregations aggregations = response.getAggregations();
        Terms brandAgg = aggregations.get("brand_agg");
        List<? extends Terms.Bucket> buckets = brandAgg.getBuckets();
        for (Terms.Bucket bucket : buckets) {
            System.out.println("brand:" + bucket.getKeyAsString());
            System.out.println("count:" + bucket.getDocCount());
        }
    }

    @Test
    void testMatchQuery() throws IOException {
        SearchRequest request = new SearchRequest("items");

        request.source()
                .query(
                        QueryBuilders.boolQuery()
                                .must(
                                        QueryBuilders.matchQuery("name", "脱脂牛奶")
                                )
                                .filter(
                                        QueryBuilders.termQuery("brand.keyword", "德亚")
                                )
                                .filter(
                                        QueryBuilders.rangeQuery("price").lt(30000)
                                )
                );

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        parseResponse(response);
    }

    @Test
    void testLimitAndSortQuery() throws IOException {
        SearchRequest request = new SearchRequest("items");
        int pageNo = 1;
        int pageSize = 5;
        request.source()
                .query(QueryBuilders.matchAllQuery());
        request.source()
                .from((pageNo - 1) * pageSize).size(pageSize);
        request.source()
                .sort("isAD", SortOrder.DESC)
                .sort("price", SortOrder.DESC)
                .sort("updateTime", SortOrder.ASC);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        parseResponse(response);
    }

    @Test
    void testHighLightQuery() throws IOException {
        SearchRequest request = new SearchRequest("items");
        request.source()
                .query(QueryBuilders.matchQuery("name", "脱脂牛奶"));
        request.source()
                .highlighter(
                        SearchSourceBuilder.highlight()
                                .field("name")
                                .preTags("<em>")
                                .postTags("</em>")
                );

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        parseResponse(response);
    }

    private static void parseResponse(SearchResponse response) {
        SearchHits hits = response.getHits();
        long value = hits.getTotalHits().value;
        System.out.println("总条数:" + value);
        SearchHit[] searchHits = hits.getHits();
        System.out.println("内容:");
        for (SearchHit hit : searchHits) {
            String source = hit.getSourceAsString();
            ItemDoc itemDoc = JSONUtil.toBean(source, ItemDoc.class);
            Map<String, HighlightField> highlightFields = hit.getHighlightFields();
            if (highlightFields != null && !highlightFields.isEmpty()) {
                HighlightField field = highlightFields.get("name");
                String highLightName = field.getFragments()[0].toString();
                itemDoc.setName(highLightName);
            }
            System.out.println(itemDoc);
        }
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