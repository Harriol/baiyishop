package com.harriol.baiyishop.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 搜索服务配置（REQ-301、REQ-302）。
 * <p>综合排序权重与新鲜度窗口按需在 Nacos 覆盖（R5-Q5）：
 * <pre>
 * 综合得分 = 销量得分 × sales-weight + 上架新鲜度 × freshness-weight
 * 销量得分 = min(1, log(1 + sales) / log(1 + sales-normalize-max))
 * 上架新鲜度 = max(0, 1 - 上架天数 / freshness-days)
 * </pre>
 *
 * @param indexAlias     索引别名（查询与增量写入都走别名，全量重建时原子切换）
 * @param elasticsearchUri ES 地址
 * @param productChangedTopic 商品变更 topic（消费端）
 * @param consumerGroup  消费组名
 * @param salesWeight    销量权重
 * @param freshnessWeight 上架新鲜度权重
 * @param salesNormalizeMax 销量归一化基准（达到该销量即得满分 1.0）
 * @param freshnessDays  新鲜度衰减天数
 * @param consumeDedupTtlDays 消费幂等键在 Redis 的保留天数
 */
@ConfigurationProperties(prefix = "baiyishop.search")
public record SearchProperties(
        @DefaultValue("baiyishop_product") String indexAlias,
        @DefaultValue("http://localhost:9200") String elasticsearchUri,
        @DefaultValue("baiyishop-product-changed") String productChangedTopic,
        @DefaultValue("baiyishop-search") String consumerGroup,
        @DefaultValue("0.6") double salesWeight,
        @DefaultValue("0.4") double freshnessWeight,
        @DefaultValue("10000") int salesNormalizeMax,
        @DefaultValue("30") double freshnessDays,
        @DefaultValue("7") int consumeDedupTtlDays) {
}
