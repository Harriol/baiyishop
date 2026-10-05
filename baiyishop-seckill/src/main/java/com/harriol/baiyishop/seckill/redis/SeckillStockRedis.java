package com.harriol.baiyishop.seckill.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 秒杀预扣的 Redis 原子操作（ADR-008 方案 A）。
 * <p>一次 Lua 往返完成五件事：活动时间校验 → 库存是否够 → 限购是否超 → 扣库存 → 累加已购。
 * 判定与写入在同一段脚本里，因此**并发下不会超卖**，也不会出现「先查后扣」的竞态。
 * <p>返回值用数字约定（0 成功 / 1 售罄 / 2 未开始 / 3 已结束 / 4 超限购 / -1 未预热），
 * 由服务层翻译成业务码与失败原因。
 */
@Component
public class SeckillStockRedis {

    public static final long RESULT_OK = 0L;
    public static final long RESULT_SOLD_OUT = 1L;
    public static final long RESULT_NOT_STARTED = 2L;
    public static final long RESULT_ENDED = 3L;
    public static final long RESULT_LIMIT_EXCEEDED = 4L;
    public static final long RESULT_NOT_WARMED = -1L;

    /** 预扣：KEYS[1]=库存 KEYS[2]=已购 KEYS[3]=活动元信息 ARGV=nowMs,quantity,limit,ttlSeconds */
    private static final RedisScript<Long> DEDUCT_SCRIPT = new DefaultRedisScript<>("""
            local stock = redis.call('GET', KEYS[1])
            if not stock then return -1 end
            local startMs = tonumber(redis.call('HGET', KEYS[3], 'startMs') or '0')
            local endMs = tonumber(redis.call('HGET', KEYS[3], 'endMs') or '0')
            local nowMs = tonumber(ARGV[1])
            if startMs > 0 and nowMs < startMs then return 2 end
            if endMs > 0 and nowMs > endMs then return 3 end
            local quantity = tonumber(ARGV[2])
            if tonumber(stock) < quantity then return 1 end
            local bought = tonumber(redis.call('GET', KEYS[2]) or '0')
            if bought + quantity > tonumber(ARGV[3]) then return 4 end
            redis.call('DECRBY', KEYS[1], quantity)
            redis.call('INCRBY', KEYS[2], quantity)
            redis.call('EXPIRE', KEYS[2], tonumber(ARGV[4]))
            return 0
            """, Long.class);

    /** 回补：KEYS[1]=库存 KEYS[2]=已购 ARGV=quantity,ttlSeconds */
    private static final RedisScript<Long> ROLLBACK_SCRIPT = new DefaultRedisScript<>("""
            redis.call('INCRBY', KEYS[1], tonumber(ARGV[1]))
            local bought = tonumber(redis.call('GET', KEYS[2]) or '0') - tonumber(ARGV[1])
            if bought <= 0 then
              redis.call('DEL', KEYS[2])
            else
              redis.call('SET', KEYS[2], bought)
              redis.call('EXPIRE', KEYS[2], tonumber(ARGV[2]))
            end
            return 1
            """, Long.class);

    /** 限流：KEYS[1]=计数 ARGV[1]=窗口内上限；返回 1 放行 / 0 限流 */
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], 1) end
            if count > tonumber(ARGV[1]) then return 0 end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;

    public SeckillStockRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 预热活动缓存：活动元信息 + 该 SKU 的可售库存。
     * <p>创建 / 修改活动时写入，抢购时若发现未预热会按库里的权威值再补一次（见 SeckillBuyService）。
     */
    public void warmUp(long activityId, long activitySkuId, int stock, long startMs, long endMs, Duration ttl) {
        String activityKey = SeckillRedisKeys.activity(activityId);
        if (startMs > 0) {
            redis.opsForHash().put(activityKey, "startMs", String.valueOf(startMs));
        }
        if (endMs > 0) {
            redis.opsForHash().put(activityKey, "endMs", String.valueOf(endMs));
        }
        redis.expire(activityKey, ttl);
        // 库存只在不存在时写入：活动进行中重新预热不能把已扣减的库存重置回初始值
        String stockKey = SeckillRedisKeys.stock(activitySkuId);
        if (Boolean.FALSE.equals(redis.hasKey(stockKey))) {
            redis.opsForValue().set(stockKey, String.valueOf(stock), ttl);
        }
    }

    public boolean isWarmed(long activitySkuId) {
        return Boolean.TRUE.equals(redis.hasKey(SeckillRedisKeys.stock(activitySkuId)));
    }

    public Integer stockOf(long activitySkuId) {
        String value = redis.opsForValue().get(SeckillRedisKeys.stock(activitySkuId));
        return value == null ? null : Integer.valueOf(value);
    }

    /** 清掉某个活动 SKU 的预扣数据（活动删除 / 重建时用） */
    public void clear(long activitySkuId) {
        redis.delete(SeckillRedisKeys.stock(activitySkuId));
    }

    public long deduct(long activityId, long activitySkuId, long userId, int quantity, int limitPerUser,
                       long ttlSeconds) {
        List<String> keys = List.of(SeckillRedisKeys.stock(activitySkuId),
                SeckillRedisKeys.bought(activitySkuId, userId),
                SeckillRedisKeys.activity(activityId));
        return nullSafe(redis.execute(DEDUCT_SCRIPT, keys, String.valueOf(System.currentTimeMillis()),
                String.valueOf(quantity), String.valueOf(limitPerUser), String.valueOf(ttlSeconds)));
    }

    /** 回补预扣（下单失败 / 订单超时取消）：库存加回、已购计数递减（需求方确认可再次抢购） */
    public void rollback(long activitySkuId, long userId, int quantity, long ttlSeconds) {
        redis.execute(ROLLBACK_SCRIPT,
                List.of(SeckillRedisKeys.stock(activitySkuId), SeckillRedisKeys.bought(activitySkuId, userId)),
                String.valueOf(quantity), String.valueOf(ttlSeconds));
    }

    /** 固定窗口限流：同一用户每秒最多 perSecond 次（REQ-906） */
    public boolean tryAcquireRateLimit(long userId, int perSecond) {
        Long allowed = redis.execute(RATE_LIMIT_SCRIPT, List.of(SeckillRedisKeys.rate(userId)),
                String.valueOf(perSecond));
        return allowed != null && allowed == 1L;
    }

    private long nullSafe(Long value) {
        return value == null ? RESULT_NOT_WARMED : value;
    }
}
