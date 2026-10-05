package com.harriol.baiyishop.order.listener;

import com.harriol.baiyishop.order.dto.PaymentSuccessEventView;
import com.harriol.baiyishop.order.service.OrderService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 支付成功事件消费（REQ-802-3、docs/architecture.md 5.2）。
 * <p>消费端在**全局事务**内把订单流转为待发货并扣减库存（OrderService.markPaid）；
 * 消息可能重复投递，业务侧靠订单状态的条件更新保证只生效一次。
 */
@Component
@RocketMQMessageListener(
        topic = "${baiyishop.order.payment-success-topic:baiyishop-payment-success}",
        consumerGroup = "baiyishop-order-payment",
        maxReconsumeTimes = 3)
public class PaymentSuccessListener implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(PaymentSuccessListener.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public PaymentSuccessListener(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(String message) {
        PaymentSuccessEventView event = objectMapper.readValue(message, PaymentSuccessEventView.class);
        log.info("收到支付成功事件 paymentNo={} orderNo={}", event.paymentNo(), event.orderNo());
        orderService.markPaid(event.orderNo(), event.channel(), event.channelTradeNo());
    }
}
