package com.harriol.baiyishop.order.listener;

import com.harriol.baiyishop.order.dto.SeckillOrderEventView;
import com.harriol.baiyishop.order.service.SeckillOrderService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 秒杀下单消息消费（REQ-903）。
 * <p>消费端幂等：票据已落单就直接回写结果；不抛异常（可预期失败已回写原因），
 * 只有依赖抖动这类不可预期异常才上抛交给重试。
 */
@Component
@RocketMQMessageListener(
        topic = "${baiyishop.order.seckill-order-topic:baiyishop-seckill-order}",
        consumerGroup = "baiyishop-order-seckill",
        maxReconsumeTimes = 3)
public class SeckillOrderListener implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderListener.class);

    private final SeckillOrderService seckillOrderService;
    private final ObjectMapper objectMapper;

    public SeckillOrderListener(SeckillOrderService seckillOrderService, ObjectMapper objectMapper) {
        this.seckillOrderService = seckillOrderService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(String message) {
        SeckillOrderEventView event = objectMapper.readValue(message, SeckillOrderEventView.class);
        log.info("收到秒杀下单消息 ticketId={} skuId={} quantity={}", event.ticketId(), event.skuId(),
                event.quantity());
        seckillOrderService.handle(event);
    }
}
