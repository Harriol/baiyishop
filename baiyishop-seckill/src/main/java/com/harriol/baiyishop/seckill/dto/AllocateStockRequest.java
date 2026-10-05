package com.harriol.baiyishop.seckill.dto;

/** 向 inventory 申请划拨（docs/api.md 第 6 章）。 */
public record AllocateStockRequest(Long activityId, Long activitySkuId, Long skuId, Long productId,
                                   Integer quantity, String batchNo) {
}
