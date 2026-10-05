package com.harriol.baiyishop.order.dto;

/** 消费的秒杀下单事件（seckill-service 的 SeckillOrderEvent）。 */
public record SeckillOrderEventView(String ticketId, Long activityId, Long activitySkuId, Long userId,
                                    Long skuId, Long productId, Integer quantity, Long seckillPrice,
                                    String requestId) {
}
