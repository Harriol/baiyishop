package com.harriol.baiyishop.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 秒杀库存回补（REQ-504、REQ-905）。
 * <p>活动结束时把未售出的剩余回补普通库存；订单超时取消时把该单占用量回补秒杀池。
 *
 * @param mode   UNSOLD 活动结束回补普通库存 / ROLLBACK 取消回滚到秒杀池
 * @param quantity ROLLBACK 时需要回滚的数量；UNSOLD 时忽略（以池内剩余为准）
 * @param orderNo  ROLLBACK 场景的订单号，参与幂等键
 */
public record SeckillReturnRequest(
        @NotNull(message = "缺少 activitySkuId") Long activitySkuId,

        @NotBlank(message = "缺少 mode") String mode,

        Integer quantity,

        String orderNo,

        @NotBlank(message = "缺少 batchNo") String batchNo) {

    public static final String MODE_UNSOLD = "UNSOLD";
    public static final String MODE_ROLLBACK = "ROLLBACK";
}