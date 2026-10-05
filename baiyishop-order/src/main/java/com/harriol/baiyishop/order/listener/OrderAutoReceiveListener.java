package com.harriol.baiyishop.order.listener;

import com.harriol.baiyishop.order.dto.OrderAutoReceiveEvent;
import com.harriol.baiyishop.order.service.OrderService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 发货 7 天后自动确认收货的延时消息消费（REQ-707）。
 * <p>同样以条件更新保证幂等：只有「待收货且已到自动收货时间」的订单会被流转一次。
 */
@Component
@RocketMQMessageListener(
        topic = "${baiyishop.order.order-auto-receive-topic:baiyishop-order-auto-receive}",
        consumerGroup = "baiyishop-order-auto-receive",
        maxReconsumeTimes = 3)
public class OrderAutoReceiveListener implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(OrderAutoReceiveListener.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public OrderAutoReceiveListener(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(String message) {
        OrderAutoReceiveEvent event = objectMapper.readValue(message, OrderAutoReceiveEvent.class);
        log.info("收到自动确认收货消息 orderNo={}", event.orderNo());
        orderService.autoReceive(event.orderNo());
    }
}
