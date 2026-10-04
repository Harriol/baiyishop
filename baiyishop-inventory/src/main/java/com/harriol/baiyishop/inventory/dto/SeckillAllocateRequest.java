package com.harriol.baiyishop.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 秒杀库存划拨（REQ-504），由 seckill-service 在建 / 改活动时调用。
 * <p>划拨即从普通库存扣减；batchNo 用于幂等，避免重试时重复划拨。
 */
public record SeckillAllocateRequest(
        @NotNull(message = "缺少 activityId") Long activityId,

        @NotNull(message = "缺少 activitySkuId") Long activitySkuId,

        @NotNull(message = "缺少 skuId") Long skuId,

        Long productId,

        @NotNull(message = "缺少划拨数量") @Min(value = 1, message = "划拨数量必须大于 0") Integer quantity,

        @NotBlank(message = "缺少 batchNo") String batchNo) {
}