package com.harriol.baiyishop.order.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 订单列表行（REQ-702）。 */
public record OrderSummary(
        String orderNo,
        String status,
        String statusLabel,
        Long payAmount,
        Integer totalQuantity,
        LocalDateTime createdAt,
        List<OrderItemView> items) {
}
