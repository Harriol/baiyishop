package com.harriol.baiyishop.product.dto;

import java.time.LocalDateTime;

/**
 * 商品变更事件（topic {@code baiyishop-product-changed}，docs/architecture.md 4.2）。
 * <p>事件只带「哪个商品变了」，不带商品内容：消费端按 productId 回查最新状态重建文档，
 * 因此消息乱序不会把旧数据写进索引（后到的消息回查到的仍是最新值）。
 *
 * @param eventId    事件 ID，消费侧幂等键
 * @param productId  商品 ID
 * @param action     UPSERT 新增 / 修改 / 上下架、DELETE 删除
 * @param occurredAt 发生时间
 */
public record ProductChangedEvent(String eventId, Long productId, String action, LocalDateTime occurredAt) {

    public static final String ACTION_UPSERT = "UPSERT";
    public static final String ACTION_DELETE = "DELETE";
}
