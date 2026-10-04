package com.harriol.baiyishop.search.dto;

import java.time.LocalDateTime;

/**
 * 消费的商品变更事件（product-service 的 {@code ProductChangedEvent}）。
 * <p>事件体只带 productId 与 action，文档内容消费时回查，因此消息乱序也安全。
 */
public record ProductChangedEventView(String eventId, Long productId, String action, LocalDateTime occurredAt) {

    public static final String ACTION_DELETE = "DELETE";

    public boolean isDelete() {
        return ACTION_DELETE.equalsIgnoreCase(action);
    }
}
