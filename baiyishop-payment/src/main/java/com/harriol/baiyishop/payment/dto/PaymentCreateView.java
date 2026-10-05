package com.harriol.baiyishop.payment.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 发起支付的结果（docs/api.md 4.8）。
 *
 * @param payParams 渠道支付参数，形状按真实渠道设计（本期为模拟值）
 */
public record PaymentCreateView(String paymentNo, String channel, Long amount,
                                Map<String, Object> payParams, LocalDateTime expireAt) {
}
