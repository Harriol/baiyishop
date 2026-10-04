package com.harriol.baiyishop.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 客户端装配（REQ-301）。
 * <p>客户端版本与服务器一致为 8.11.3：Boot 4 默认管理 9.x 客户端，其
 * {@code compatible-with=9} 请求头会被 8.x 服务器拒绝（见 libs.versions.toml）。
 * <p>同时暴露低层 {@link RestClient}：索引创建、别名切换这类「一次性运维动作」
 * 直接用 JSON 更贴近 ES 官方文档，排查时能逐字对照。
 */
@Configuration
@EnableConfigurationProperties(SearchProperties.class)
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    public RestClient elasticsearchRestClient(SearchProperties properties) {
        return RestClient.builder(HttpHost.create(properties.elasticsearchUri())).build();
    }

    @Bean
    public JsonpMapper elasticsearchJsonpMapper() {
        return new JacksonJsonpMapper();
    }

    @Bean(destroyMethod = "close")
    public ElasticsearchTransport elasticsearchTransport(RestClient elasticsearchRestClient, JsonpMapper mapper) {
        return new RestClientTransport(elasticsearchRestClient, mapper);
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }

}
