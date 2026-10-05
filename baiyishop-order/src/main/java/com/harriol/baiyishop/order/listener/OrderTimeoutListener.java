package com.harriol.baiyishop.order.listener;

import com.harriol.baiyishop.order.dto.OrderTimeoutEvent;
import com.harriol.baiyishop.order.service.OrderService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 15 分钟未支付自动取消的延时消息消费（REQ-704）。
 * <p>幂等由消费端自己保证：条件更新「待付款 + 已超时」，重复投递或提前到达都是空操作，
 * 因此消息重投不需要去重表。
 */
@Component
@RocketMQMessageListener(
        topic = "${baiyishop.order.order-timeout-topic:baiyishop-order-timeout}",
        consumerGroup = "baiyishop-order-timeout",
        maxReconsumeTimes = 3)
public class OrderTimeoutListener implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutListener.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public OrderTimeoutListener(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(String message) {
        OrderTimeoutEvent event = objectMapper.readValue(message, OrderTimeoutEvent.class);
        log.info("收到订单超时消息 orderNo={}", event.orderNo());
        orderService.cancelByTimeout(event.orderNo());
    }
}
