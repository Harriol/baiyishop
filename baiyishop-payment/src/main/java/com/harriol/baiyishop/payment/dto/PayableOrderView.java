package com.harriol.baiyishop.payment.dto;

import java.time.LocalDateTime;

/**
 * order-service 的「订单可支付性」视图（docs/api.md 第 6 章）。
 * <p>金额与超时时间来自订单，支付单必须与之一致：金额不等说明订单被改过或前端传错，直接拒绝（REQ-801）。
 */
public record PayableOrderView(String orderNo, Long userId, String status, Long payAmount, LocalDateTime timeoutAt) {
}
