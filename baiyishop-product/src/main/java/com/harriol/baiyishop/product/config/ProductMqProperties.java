package com.harriol.baiyishop.product.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 商品域消息配置（docs/adr/ADR-005）。
 *
 * @param productChangedTopic 商品变更 topic，search-service 消费
 * @param outboxMaxRetry      投递失败的最大重试次数，超过后置为 FAILED 等待人工重放
 */
@ConfigurationProperties(prefix = "baiyishop.mq")
public record ProductMqProperties(
        @DefaultValue("baiyishop-product-changed") String productChangedTopic,
        @DefaultValue("10") int outboxMaxRetry) {
}
