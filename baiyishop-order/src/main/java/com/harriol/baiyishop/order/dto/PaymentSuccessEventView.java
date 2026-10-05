package com.harriol.baiyishop.order.dto;

import java.time.LocalDateTime;

/** 消费的支付成功事件（payment-service 的 PaymentSuccessEvent）。 */
public record PaymentSuccessEventView(String paymentNo, String orderNo, String channel,
                                      String channelTradeNo, Long amount, LocalDateTime payTime) {
}
