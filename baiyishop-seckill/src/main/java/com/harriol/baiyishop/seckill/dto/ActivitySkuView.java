package com.harriol.baiyishop.seckill.dto;

/**
 * 活动商品视图。
 *
 * @param remaining 秒杀池剩余量（来自 inventory，权威值；池子未建时为 null）
 */
public record ActivitySkuView(Long id, Long skuId, Long productId, String productName, String image,
                              Long seckillPrice, Long originalPrice, Integer allocStock,
                              Integer remaining, Integer limitPerUser, Integer sort) {
}
