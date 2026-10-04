package com.harriol.baiyishop.inventory.dto;

import java.time.LocalDateTime;

/** 秒杀池现状（供 seckill-service 展示剩余量 / 对账用）。 */
public record SeckillPoolItem(Long id, Long activityId, Long activitySkuId, Long skuId,
                              Integer total, Integer remaining, Integer sold, LocalDateTime updatedAt) {
}