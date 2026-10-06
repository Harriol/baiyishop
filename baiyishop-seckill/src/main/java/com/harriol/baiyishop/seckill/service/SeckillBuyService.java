package com.harriol.baiyishop.seckill.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.seckill.client.ProductClient;
import com.harriol.baiyishop.seckill.config.SeckillProperties;
import com.harriol.baiyishop.seckill.dto.SeckillBuyRequest;
import com.harriol.baiyishop.seckill.dto.SeckillOrderEvent;
import com.harriol.baiyishop.seckill.dto.SeckillTicketView;
import com.harriol.baiyishop.seckill.dto.SkuSnapshot;
import com.harriol.baiyishop.seckill.entity.SeckillActivity;
import com.harriol.baiyishop.seckill.entity.SeckillActivitySku;
import com.harriol.baiyishop.seckill.entity.SeckillRecord;
import com.harriol.baiyishop.seckill.mapper.SeckillActivitySkuMapper;
import com.harriol.baiyishop.seckill.mapper.SeckillRecordMapper;
import com.harriol.baiyishop.seckill.redis.SeckillStockRedis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 抢购受理（REQ-903、REQ-904、REQ-906）。
 * <p>流程刻意与「下单」解耦：这里只做「限流 → 幂等 → Redis 原子预扣 → 落排队记录 + 下单消息」，
 * 真正的建单由 order-service 异步完成（ADR-008 方案 A）。因此**抢购接口不碰订单库、不做跨服务写**，
 * 1000 QPS 的判定几乎全在 Redis 一次 Lua 往返里。
 * <p>失败语义分两类：
 * <ul>
 *   <li>**预扣阶段失败**（售罄 / 未开始 / 已结束 / 超限购）：当场返回对应业务码，不产生票据</li>
 *   <li>**异步下单失败**：票据状态为 FAILED 并带原因，由 {@link SeckillResultService} 回写（REQ-904）</li>
 * </ul>
 */
@Service
public class SeckillBuyService {

    private static final Logger log = LoggerFactory.getLogger(SeckillBuyService.class);

    private static final String TAG_SECKILL_ORDER = "SECKILL_ORDER";
    private static final DateTimeFormatter TICKET_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final long MIN_KEY_TTL_SECONDS = 3600L;

    private final SeckillRecordMapper recordMapper;
    private final SeckillActivitySkuMapper activitySkuMapper;
    private final SeckillActivityService activityService;
    private final ProductClient productClient;
    private final SeckillStockRedis stockRedis;
    private final SeckillQueueWriter queueWriter;
    private final SeckillProperties properties;

    public SeckillBuyService(SeckillRecordMapper recordMapper,
                             SeckillActivitySkuMapper activitySkuMapper,
                             SeckillActivityService activityService,
                             ProductClient productClient,
                             SeckillStockRedis stockRedis,
                             SeckillQueueWriter queueWriter,
                             SeckillProperties properties) {
        this.recordMapper = recordMapper;
        this.activitySkuMapper = activitySkuMapper;
        this.activityService = activityService;
        this.productClient = productClient;
        this.stockRedis = stockRedis;
        this.queueWriter = queueWriter;
        this.properties = properties;
    }

    public SeckillTicketView buy(long userId, long activitySkuId, SeckillBuyRequest request) {
        // ① 防刷：同一用户每秒最多 N 次（REQ-906）
        if (!stockRedis.tryAcquireRateLimit(userId, properties.rateLimitPerSecond())) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS);
        }
        if (request == null || !StringUtils.hasText(request.requestId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 requestId");
        }
        int quantity = request.quantity() == null ? 1 : request.quantity();
        if (quantity < 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "抢购数量至少为 1");
        }

        // ② 请求级幂等：同一用户 + 同一活动 SKU + 同一 requestId 直接返回已有票据（REQ-903）
        SeckillRecord existing = recordMapper.selectOne(Wrappers.<SeckillRecord>lambdaQuery()
                .eq(SeckillRecord::getActivitySkuId, activitySkuId)
                .eq(SeckillRecord::getUserId, userId)
                .eq(SeckillRecord::getRequestId, request.requestId()));
        if (existing != null) {
            log.info("重复抢购请求命中幂等 ticketId={}", existing.getTicketId());
            return new SeckillTicketView(existing.getTicketId(), existing.getStatus());
        }

        SeckillActivitySku activitySku = requireActivitySku(activitySkuId);
        SeckillActivity activity = activityService.require(activitySku.getActivityId());
        if (SeckillActivity.STATUS_ENDED.equals(activity.getStatus())) {
            // 状态可能比时间窗更早变化（后台提前结束活动），以状态为准先拒掉
            throw new BizException(ErrorCode.SECKILL_ENDED);
        }

        // ③ 商品必须仍可售：避免给已下架的商品发券
        SkuSnapshot sku = productClient.sku(activitySku.getSkuId());
        if (!sku.sellable()) {
            throw new BizException(ErrorCode.SECKILL_SKU_NOT_ON_SALE);
        }

        // ④ 预热（幂等）：库存键缺失时按权威值补一次，避免 Redis 重启后无法抢购
        warmIfNeeded(activity, activitySku);

        // ⑤ 一次 Lua 原子完成「时间校验 + 库存 + 限购 + 扣减」（ADR-008 方案 A）
        long result = stockRedis.deduct(activity.getId(), activitySkuId, userId, quantity,
                activitySku.getLimitPerUser() == null ? 1 : activitySku.getLimitPerUser(),
                ttlSeconds(activity));
        requireDeducted(result);

        // ⑥ 落排队记录 + 下单消息（同一本地事务）
        SeckillRecord record = new SeckillRecord();
        record.setTicketId(nextTicketId());
        record.setActivityId(activity.getId());
        record.setActivitySkuId(activitySkuId);
        record.setUserId(userId);
        record.setRequestId(request.requestId());
        record.setQuantity(quantity);
        record.setStatus(SeckillRecord.STATUS_QUEUED);
        try {
            queueWriter.saveQueued(record, properties.orderTopic(), TAG_SECKILL_ORDER,
                    new SeckillOrderEvent(record.getTicketId(), activity.getId(), activitySku.getId(),
                            record.getUserId(), activitySku.getSkuId(), activitySku.getProductId(),
                            record.getQuantity(), activitySku.getSeckillPrice(), record.getRequestId()));
        } catch (DataIntegrityViolationException ex) {
            // 并发重复请求：uk_user_request 命中，回滚本次预扣后返回首次票据
            stockRedis.rollback(activitySkuId, userId, quantity, ttlSeconds(activity));
            SeckillRecord first = recordMapper.selectOne(Wrappers.<SeckillRecord>lambdaQuery()
                    .eq(SeckillRecord::getActivitySkuId, activitySkuId)
                    .eq(SeckillRecord::getUserId, userId)
                    .eq(SeckillRecord::getRequestId, request.requestId()));
            if (first == null) {
                throw ex;
            }
            log.info("并发重复抢购命中唯一索引 ticketId={}", first.getTicketId());
            return new SeckillTicketView(first.getTicketId(), first.getStatus());
        }
        log.info("抢购预扣成功 ticketId={} activitySkuId={} userId={} quantity={}", record.getTicketId(),
                activitySkuId, userId, quantity);
        return new SeckillTicketView(record.getTicketId(), SeckillRecord.STATUS_QUEUED);
    }

    private void warmIfNeeded(SeckillActivity activity, SeckillActivitySku activitySku) {
        if (!stockRedis.isWarmed(activitySku.getId())) {
            activityService.warmUp(activity, java.util.List.of(activitySku));
            log.info("补预热秒杀库存 activitySkuId={} stock={}", activitySku.getId(), activitySku.getAllocStock());
        }
    }

    private void requireDeducted(long result) {
        if (result == SeckillStockRedis.RESULT_OK) {
            return;
        }
        if (result == SeckillStockRedis.RESULT_SOLD_OUT) {
            throw new BizException(ErrorCode.SECKILL_SOLD_OUT);
        }
        if (result == SeckillStockRedis.RESULT_NOT_STARTED) {
            throw new BizException(ErrorCode.SECKILL_NOT_STARTED);
        }
        if (result == SeckillStockRedis.RESULT_ENDED) {
            throw new BizException(ErrorCode.SECKILL_ENDED);
        }
        if (result == SeckillStockRedis.RESULT_LIMIT_EXCEEDED) {
            throw new BizException(ErrorCode.SECKILL_LIMIT_EXCEEDED);
        }
        // 未预热：数据异常，按系统繁忙处理（对账任务会把 Redis 补回来）
        throw new BizException(ErrorCode.SYSTEM_ERROR, "秒杀库存未就绪，请稍后重试");
    }

    private SeckillActivitySku requireActivitySku(long activitySkuId) {
        SeckillActivitySku activitySku = activitySkuMapper.selectById(activitySkuId);
        if (activitySku == null) {
            throw new BizException(ErrorCode.SECKILL_ACTIVITY_NOT_FOUND, "秒杀商品不存在");
        }
        return activitySku;
    }

    /** 已购计数的 TTL：活动结束后还要保留一天，便于对账与展示 */
    private long ttlSeconds(SeckillActivity activity) {
        long seconds = Duration.between(LocalDateTime.now(), activity.getEndTime()).toSeconds()
                + Duration.ofDays(1).toSeconds();
        return Math.max(seconds, MIN_KEY_TTL_SECONDS);
    }

    private String nextTicketId() {
        return "TK" + LocalDateTime.now().format(TICKET_TIME)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
