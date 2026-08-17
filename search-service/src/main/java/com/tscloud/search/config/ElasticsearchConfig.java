package com.tscloud.search.config;

import lombok.Data;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Bean
    @ConfigurationProperties(prefix = "ts.es")
    public EsProperties esProperties() {
        return new EsProperties();
    }

    @Bean(destroyMethod = "close")
    public RestHighLevelClient restHighLevelClient(EsProperties properties) {
        return new RestHighLevelClient(RestClient.builder(
                HttpHost.create(properties.getUri())
        ));
    }

    @Data
    public static class EsProperties {
        private String uri;
    }
}