package com.harriol.baiyishop.seckill.dto;

/** 活动里的单个商品（REQ-901）。 */
public record ActivitySkuRequest(Long skuId, Long seckillPrice, Integer allocStock, Integer limitPerUser,
                                 Integer sort) {
}
