package com.harriol.baiyishop.order.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 订单详情（REQ-702）：明细快照 + 收货信息 + 状态日志 + 各节点时间。 */
public record OrderDetailView(
        String orderNo,
        String status,
        String statusLabel,
        String source,
        Long totalAmount,
        Long freightAmount,
        Long payAmount,
        String remark,
        String cancelReason,
        String trackingNo,
        LocalDateTime timeoutAt,
        LocalDateTime payTime,
        LocalDateTime shipTime,
        LocalDateTime receiveTime,
        LocalDateTime finishTime,
        LocalDateTime cancelTime,
        LocalDateTime createdAt,
        ReceiverView receiver,
        List<OrderItemView> items,
        List<StatusLogView> statusLogs) {
}
