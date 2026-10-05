package com.harriol.baiyishop.seckill.listener;

import com.harriol.baiyishop.seckill.dto.OrderCancelEvent;
import com.harriol.baiyishop.seckill.service.SeckillResultService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 秒杀订单取消 / 超时消息（REQ-905）：回补秒杀池并恢复限购计数。
 * <p>幂等由记录状态（已 FAILED 直接返回）与回补批次的 orderNo 幂等键共同保证。
 */
@Component
@RocketMQMessageListener(
        topic = "${baiyishop.seckill.cancel-topic:baiyishop-seckill-order-cancel}",
        consumerGroup = "baiyishop-seckill-cancel",
        maxReconsumeTimes = 3)
public class SeckillOrderCancelListener implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderCancelListener.class);

    private final SeckillResultService resultService;
    private final ObjectMapper objectMapper;

    public SeckillOrderCancelListener(SeckillResultService resultService, ObjectMapper objectMapper) {
        this.resultService = resultService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(String message) {
        OrderCancelEvent event = objectMapper.readValue(message, OrderCancelEvent.class);
        log.info("收到秒杀订单取消消息 orderNo={}", event.orderNo());
        resultService.onOrderCancelled(event.orderNo());
    }
}
