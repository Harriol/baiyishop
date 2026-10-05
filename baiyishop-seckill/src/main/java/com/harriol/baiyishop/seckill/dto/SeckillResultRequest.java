package com.harriol.baiyishop.seckill.dto;

/**
 * order-service 回写的抢购结果（内部接口）。
 * <p>失败时秒杀侧会**回补 Redis 预扣**，让库存与限购计数回到可再次抢购的状态（ADR-008 失败补偿）。
 */
public record SeckillResultRequest(String status, String orderNo, String failReason, String message) {
}
