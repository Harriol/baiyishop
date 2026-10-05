package com.harriol.baiyishop.payment.dto;

/** 发起支付（docs/api.md 4.8、REQ-801）。 */
public record CreatePaymentRequest(String orderNo, String channel) {
}
