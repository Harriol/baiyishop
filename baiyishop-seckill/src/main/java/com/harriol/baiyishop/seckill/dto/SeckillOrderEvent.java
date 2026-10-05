package com.harriol.baiyishop.seckill.dto;

/**
 * 秒杀下单事件（topic {@code baiyishop-seckill-order}，docs/architecture.md 5.4）。
 * <p>只带下单所需的最小信息；order-service 落单后回调
 * {@code POST /internal/seckill/records/{ticketId}/result} 回写结果。
 */
public record SeckillOrderEvent(String ticketId, Long activityId, Long activitySkuId, Long userId,
                                Long skuId, Long productId, Integer quantity, Long seckillPrice,
                                String requestId) {
}
