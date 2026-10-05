package com.harriol.baiyishop.order.dto;

import com.harriol.baiyishop.order.entity.OrderStatusLog;

import java.time.LocalDateTime;

/** 状态流转留痕视图（REQ-703）。 */
public record StatusLogView(String fromStatus, String toStatus, String operatorType,
                            Long operatorId, String reason, LocalDateTime createdAt) {

    public static StatusLogView from(OrderStatusLog log) {
        return new StatusLogView(log.getFromStatus(), log.getToStatus(), log.getOperatorType(),
                log.getOperatorId(), log.getReason(), log.getCreatedAt());
    }
}
