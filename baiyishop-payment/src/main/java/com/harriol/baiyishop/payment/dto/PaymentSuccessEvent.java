package com.harriol.baiyishop.payment.dto;

import java.time.LocalDateTime;

/**
 * 支付成功事件（topic baiyishop-payment-success，docs/architecture.md 4.2、5.2）。
 * <p>order-service 消费它并在全局事务内把订单流转为待发货 + 扣减库存（REQ-802-3）。
 * 事件带 paymentNo / channelTradeNo，便于与渠道对账排查。
 */
public record PaymentSuccessEvent(String paymentNo, String orderNo, String channel,
                                  String channelTradeNo, Long amount, LocalDateTime payTime) {
}
