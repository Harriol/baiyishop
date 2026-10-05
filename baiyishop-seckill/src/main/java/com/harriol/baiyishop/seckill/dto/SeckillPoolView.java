package com.harriol.baiyishop.seckill.dto;

import java.time.LocalDateTime;

/** inventory-service 的秒杀池视图（docs/api.md 第 6 章）。 */
public record SeckillPoolView(Long id, Long activityId, Long activitySkuId, Long skuId,
                              Integer total, Integer remaining, Integer sold, LocalDateTime updatedAt) {
}
