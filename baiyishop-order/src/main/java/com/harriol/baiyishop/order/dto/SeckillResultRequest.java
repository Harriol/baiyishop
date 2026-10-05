package com.harriol.baiyishop.order.dto;

/** 回写给 seckill-service 的抢购结果（内部接口）。 */
public record SeckillResultRequest(String status, String orderNo, String failReason, String message) {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    public static SeckillResultRequest success(String orderNo) {
        return new SeckillResultRequest(STATUS_SUCCESS, orderNo, null, null);
    }

    public static SeckillResultRequest failed(String failReason, String message) {
        return new SeckillResultRequest(STATUS_FAILED, null, failReason, message);
    }
}
