package com.harriol.baiyishop.seckill.redis;

/**
 * 秒杀相关的 Redis key 约定（ADR-008）。
 * <p>三类数据：活动元信息（含起止时间，供 Lua 前置校验）、预扣后的剩余库存、用户的已购计数。
 */
public final class SeckillRedisKeys {

    private static final String PREFIX = "baiyishop:seckill:";

    private SeckillRedisKeys() {
    }

    /** 活动元信息（hash：startMs / endMs / status），Lua 里用它判断活动是否在时间窗口内 */
    public static String activity(long activityId) {
        return PREFIX + "activity:" + activityId;
    }

    /** 预扣后的剩余秒杀库存 */
    public static String stock(long activitySkuId) {
        return PREFIX + "stock:" + activitySkuId;
    }

    /** 某用户在某活动 SKU 上的已购数量（限购校验 + 回补时递减） */
    public static String bought(long activitySkuId, long userId) {
        return PREFIX + "bought:" + activitySkuId + ":" + userId;
    }

    /** 单用户限流计数（固定窗口 1 秒，REQ-906） */
    public static String rate(long userId) {
        return PREFIX + "rate:" + userId;
    }
}
