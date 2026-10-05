package com.harriol.baiyishop.seckill.dto;

/** 秒杀池回补（UNSOLD 活动结束 / ROLLBACK 订单取消）。 */
public record ReturnStockRequest(Long activitySkuId, String mode, Integer quantity, String orderNo,
                                 String batchNo) {

    public static final String MODE_UNSOLD = "UNSOLD";
    public static final String MODE_ROLLBACK = "ROLLBACK";
}
