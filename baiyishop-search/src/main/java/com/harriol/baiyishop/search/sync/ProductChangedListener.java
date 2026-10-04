package com.harriol.baiyishop.search.sync;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 商品变更消息消费入口（REQ-302）。
 * <p>topic 与消费组可由 Nacos 覆盖；重试 3 次后进死信，避免一条坏消息无限重投。
 * <p>消费逻辑抛异常即触发重投，因此这里不做 try/catch：吞掉异常等于确认消息，索引就悄悄落后了。
 */
@Component
@RocketMQMessageListener(
        topic = "${baiyishop.search.product-changed-topic:baiyishop-product-changed}",
        consumerGroup = "${baiyishop.search.consumer-group:baiyishop-search}",
        maxReconsumeTimes = 3)
public class ProductChangedListener implements RocketMQListener<String> {

    private final ProductChangeSyncService syncService;

    public ProductChangedListener(ProductChangeSyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    public void onMessage(String message) {
        syncService.handle(message);
    }
}
