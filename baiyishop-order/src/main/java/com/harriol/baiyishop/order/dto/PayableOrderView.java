package com.harriol.baiyishop.order.dto;

import com.harriol.baiyishop.order.entity.Order;

import java.time.LocalDateTime;

/**
 * 订单可支付性视图（内部接口，供 payment-service 使用，REQ-801）。
 * <p>只回支付方需要的四个字段：归属、状态、金额、超时时间 —— 支付方据此校验，
 * 校验不过就不创建支付单。
 */
public record PayableOrderView(String orderNo, Long userId, String status, Long payAmount,
                               LocalDateTime timeoutAt) {

    public static PayableOrderView from(Order order) {
        return new PayableOrderView(order.getOrderNo(), order.getUserId(), order.getStatus(),
                order.getPayAmount(), order.getTimeoutAt());
    }
}
