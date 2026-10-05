package com.harriol.baiyishop.order.dto;

import java.time.LocalDateTime;

/** 后台订单列表行（REQ-708）。 */
public record AdminOrderItem(
        String orderNo,
        Long userId,
        String status,
        String statusLabel,
        Long payAmount,
        String receiverName,
        String receiverPhone,
        String trackingNo,
        LocalDateTime createdAt,
        LocalDateTime shipTime) {
}
