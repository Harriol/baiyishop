package com.harriol.baiyishop.payment.dto;

import com.harriol.baiyishop.payment.entity.Payment;

import java.time.LocalDateTime;

/** 支付单查询结果（REQ-803）。 */
public record PaymentView(String paymentNo, String orderNo, String channel, Long amount, String status,
                          String channelTradeNo, LocalDateTime payTime, LocalDateTime expireAt,
                          LocalDateTime createdAt) {

    public static PaymentView from(Payment payment) {
        return new PaymentView(payment.getPaymentNo(), payment.getOrderNo(), payment.getChannel(),
                payment.getAmount(), payment.getStatus(), payment.getChannelTradeNo(),
                payment.getPayTime(), payment.getExpireAt(), payment.getCreatedAt());
    }
}
