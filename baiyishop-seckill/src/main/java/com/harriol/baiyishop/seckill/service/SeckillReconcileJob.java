package com.harriol.baiyishop.seckill.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.seckill.client.InventoryClient;
import com.harriol.baiyishop.seckill.client.OrderClient;
import com.harriol.baiyishop.seckill.config.SeckillProperties;
import com.harriol.baiyishop.seckill.dto.SeckillPoolView;
import com.harriol.baiyishop.seckill.entity.SeckillActivity;
import com.harriol.baiyishop.seckill.entity.SeckillActivitySku;
import com.harriol.baiyishop.seckill.entity.SeckillRecord;
import com.harriol.baiyishop.seckill.mapper.SeckillActivityMapper;
import com.harriol.baiyishop.seckill.mapper.SeckillRecordMapper;
import com.harriol.baiyishop.seckill.redis.SeckillStockRedis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀对账与补偿（ADR-008 第 5 条）。
 * <p>两件事：
 * <ol>
 *   <li>**排队记录兜底**：QUEUED 超过阈值仍未落单的票据，先按 ticketId 回查订单
 *       —— 订单其实已存在（只是回写失败）就补成 SUCCESS，否则回补 Redis 并置 FAILED。
 *       这样「预扣成功但没下单」不会长期占用库存</li>
 *   <li>**Redis 与 MySQL 对账**：以 MySQL 的秒杀池为准修正 Redis，但**只修偏大的方向**
 *       （Redis 认为的库存比池子实际可售还多会超卖）；
 *       反向偏差可能只是「预扣了还没落单」的在途量，不能盲目补</li>
 * </ol>
 */
@Component
public class SeckillReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(SeckillReconcileJob.class);

    private static final int BATCH_SIZE = 100;

    private final SeckillRecordMapper recordMapper;
    private final SeckillActivityMapper activityMapper;
    private final OrderClient orderClient;
    private final InventoryClient inventoryClient;
    private final SeckillStockRedis stockRedis;
    private final SeckillResultService resultService;
    private final SeckillActivityService activityService;
    private final SeckillProperties properties;

    public SeckillReconcileJob(SeckillRecordMapper recordMapper,
                               SeckillActivityMapper activityMapper,
                               OrderClient orderClient,
                               InventoryClient inventoryClient,
                               SeckillStockRedis stockRedis,
                               SeckillResultService resultService,
                               SeckillActivityService activityService,
                               SeckillProperties properties) {
        this.recordMapper = recordMapper;
        this.activityMapper = activityMapper;
        this.orderClient = orderClient;
        this.inventoryClient = inventoryClient;
        this.stockRedis = stockRedis;
        this.resultService = resultService;
        this.activityService = activityService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${baiyishop.seckill.reconcile-delay:1m}")
    public void reconcile() {
        reconcileQueuedRecords();
        reconcileRedisStock();
    }

    private void reconcileQueuedRecords() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(properties.queuedTimeoutMinutes());
        List<SeckillRecord> queued = recordMapper.selectList(Wrappers.<SeckillRecord>lambdaQuery()
                .eq(SeckillRecord::getStatus, SeckillRecord.STATUS_QUEUED)
                .le(SeckillRecord::getCreatedAt, deadline)
                .orderByAsc(SeckillRecord::getId)
                .last("LIMIT " + BATCH_SIZE));
        for (SeckillRecord record : queued) {
            try {
                String orderNo = orderClient.orderNoOfTicket(record.getTicketId());
                if (orderNo != null) {
                    // 订单其实落成了，只是回写没到达：补写结果即可
                    resultService.applyResult(record.getTicketId(),
                            new com.harriol.baiyishop.seckill.dto.SeckillResultRequest("SUCCESS", orderNo, null, null));
                    log.info("对账补写抢购成功 ticketId={} orderNo={}", record.getTicketId(), orderNo);
                } else {
                    resultService.applyResult(record.getTicketId(),
                            new com.harriol.baiyishop.seckill.dto.SeckillResultRequest(
                                    "FAILED", null, SeckillRecord.FAIL_SYSTEM_ERROR, "排队超时未落单"));
                    log.warn("排队超时已回补 ticketId={}", record.getTicketId());
                }
            } catch (RuntimeException ex) {
                log.warn("排队记录对账失败 ticketId={}", record.getTicketId(), ex);
            }
        }
    }

    private void reconcileRedisStock() {
        List<SeckillActivity> running = activityMapper.selectList(Wrappers.<SeckillActivity>lambdaQuery()
                .eq(SeckillActivity::getStatus, SeckillActivity.STATUS_RUNNING)
                .last("LIMIT " + BATCH_SIZE));
        for (SeckillActivity activity : running) {
            for (SeckillActivitySku sku : activityService.skusOf(activity.getId())) {
                try {
                    Integer redisStock = stockRedis.stockOf(sku.getId());
                    SeckillPoolView pool = inventoryClient.poolOrNull(sku.getId());
                    if (redisStock == null) {
                        // Redis 丢了（重启 / 淘汰）：按权威值重新预热。
                        // 优先用池子的剩余量而不是活动配置的划拨量 —— 已售出的部分不能再放出来
                        int stock = pool == null || pool.remaining() == null
                                ? sku.getAllocStock() : pool.remaining();
                        stockRedis.resetStock(sku.getId(), stock, java.time.Duration.ofDays(1));
                        activityService.warmUp(activity, List.of(sku));
                        continue;
                    }
                    if (pool == null || pool.remaining() == null) {
                        continue;
                    }
                    if (redisStock > pool.remaining()) {
                        stockRedis.resetStock(sku.getId(), pool.remaining(), java.time.Duration.ofDays(1));
                        log.warn("Redis 秒杀库存偏大已按池子修正 activitySkuId={} redis={} pool={}",
                                sku.getId(), redisStock, pool.remaining());
                    }
                } catch (RuntimeException ex) {
                    log.debug("Redis 对账跳过 activitySkuId={}", sku.getId());
                }
            }
        }
    }

    private long epochMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
