package com.harriol.baiyishop.seckill.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.seckill.client.InventoryClient;
import com.harriol.baiyishop.seckill.dto.ReturnStockRequest;
import com.harriol.baiyishop.seckill.dto.SeckillResultRequest;
import com.harriol.baiyishop.seckill.dto.SeckillResultView;
import com.harriol.baiyishop.seckill.entity.SeckillActivity;
import com.harriol.baiyishop.seckill.entity.SeckillRecord;
import com.harriol.baiyishop.seckill.mapper.SeckillRecordMapper;
import com.harriol.baiyishop.seckill.redis.SeckillStockRedis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 抢购结果：轮询查询、订单侧回写、订单取消回补（REQ-904、REQ-905）。
 * <p>回补的两处落点必须一起动：MySQL 的秒杀池（inventory，权威）+ Redis 的预扣与限购计数。
 * 前者用 orderNo 参与幂等键，后者用 Lua 脚本保证「库存加回、已购递减」原子完成。
 */
@Service
public class SeckillResultService {

    private static final Logger log = LoggerFactory.getLogger(SeckillResultService.class);

    public static final String FAIL_ORDER_CANCELLED = "ORDER_CANCELLED";

    private static final Map<String, String> MESSAGES = Map.of(
            SeckillRecord.FAIL_SOLD_OUT, "秒杀商品已售罄",
            SeckillRecord.FAIL_LIMIT_EXCEEDED, "超出限购数量",
            SeckillRecord.FAIL_NOT_STARTED, "秒杀尚未开始",
            SeckillRecord.FAIL_ENDED, "秒杀已结束",
            SeckillRecord.FAIL_NO_ADDRESS, "请先设置收货地址后重试",
            SeckillRecord.FAIL_SYSTEM_ERROR, "系统繁忙，请稍后重试",
            FAIL_ORDER_CANCELLED, "订单已取消，可重新抢购");

    private static final long MIN_KEY_TTL_SECONDS = 3600L;

    private final SeckillRecordMapper recordMapper;
    private final InventoryClient inventoryClient;
    private final SeckillStockRedis stockRedis;
    private final SeckillActivityService activityService;

    public SeckillResultService(SeckillRecordMapper recordMapper,
                                InventoryClient inventoryClient,
                                SeckillStockRedis stockRedis,
                                SeckillActivityService activityService) {
        this.recordMapper = recordMapper;
        this.inventoryClient = inventoryClient;
        this.stockRedis = stockRedis;
        this.activityService = activityService;
    }

    /** 前端轮询抢购结果（REQ-904）；只能查自己的票据 */
    public SeckillResultView result(long userId, String ticketId) {
        SeckillRecord record = recordMapper.selectOne(Wrappers.<SeckillRecord>lambdaQuery()
                .eq(SeckillRecord::getTicketId, ticketId)
                .eq(SeckillRecord::getUserId, userId));
        if (record == null) {
            throw new BizException(ErrorCode.SECKILL_RECORD_NOT_FOUND);
        }
        return view(record);
    }

    /** order-service 回写下单结果；失败时回补 Redis 预扣（ADR-008 失败补偿） */
    @Transactional(rollbackFor = Exception.class)
    public void applyResult(String ticketId, SeckillResultRequest request) {
        SeckillRecord record = recordMapper.selectOne(Wrappers.<SeckillRecord>lambdaQuery()
                .eq(SeckillRecord::getTicketId, ticketId));
        if (record == null) {
            log.warn("回写抢购结果找不到票据 ticketId={}", ticketId);
            return;
        }
        if (!SeckillRecord.STATUS_QUEUED.equals(record.getStatus())) {
            log.info("票据已是终态，忽略回写 ticketId={} status={}", ticketId, record.getStatus());
            return;
        }
        if (SeckillRecord.STATUS_SUCCESS.equalsIgnoreCase(request.status())) {
            recordMapper.update(null, Wrappers.<SeckillRecord>lambdaUpdate()
                    .eq(SeckillRecord::getId, record.getId())
                    .eq(SeckillRecord::getStatus, SeckillRecord.STATUS_QUEUED)
                    .set(SeckillRecord::getStatus, SeckillRecord.STATUS_SUCCESS)
                    .set(SeckillRecord::getOrderNo, request.orderNo()));
            log.info("抢购成功 ticketId={} orderNo={}", ticketId, request.orderNo());
            return;
        }
        String reason = StringUtils.hasText(request.failReason())
                ? request.failReason() : SeckillRecord.FAIL_SYSTEM_ERROR;
        recordMapper.update(null, Wrappers.<SeckillRecord>lambdaUpdate()
                .eq(SeckillRecord::getId, record.getId())
                .eq(SeckillRecord::getStatus, SeckillRecord.STATUS_QUEUED)
                .set(SeckillRecord::getStatus, SeckillRecord.STATUS_FAILED)
                .set(SeckillRecord::getFailReason, reason));
        rollbackRedis(record);
        log.warn("抢购失败 ticketId={} reason={} message={}", ticketId, reason, request.message());
    }

    /**
     * 秒杀订单取消 / 超时（REQ-905）：把该单占用的数量回补到秒杀池，并恢复限购计数。
     * <p>幂等由两处保证：秒杀池回补以 orderNo 参与幂等键；记录已 FAILED 时直接返回。
     */
    @Transactional(rollbackFor = Exception.class)
    public void onOrderCancelled(String orderNo) {
        SeckillRecord record = recordMapper.selectOne(Wrappers.<SeckillRecord>lambdaQuery()
                .eq(SeckillRecord::getOrderNo, orderNo)
                .orderByDesc(SeckillRecord::getId)
                .last("LIMIT 1"));
        if (record == null) {
            log.info("订单不是秒杀订单，无需回补 orderNo={}", orderNo);
            return;
        }
        if (SeckillRecord.STATUS_FAILED.equals(record.getStatus())) {
            log.info("秒杀记录已回补过，忽略 orderNo={}", orderNo);
            return;
        }
        inventoryClient.returnStock(new ReturnStockRequest(record.getActivitySkuId(),
                ReturnStockRequest.MODE_ROLLBACK, record.getQuantity(), orderNo, "SEK-CANCEL-" + orderNo));
        rollbackRedis(record);
        recordMapper.update(null, Wrappers.<SeckillRecord>lambdaUpdate()
                .eq(SeckillRecord::getId, record.getId())
                .set(SeckillRecord::getStatus, SeckillRecord.STATUS_FAILED)
                .set(SeckillRecord::getFailReason, FAIL_ORDER_CANCELLED));
        log.info("秒杀订单取消已回补池子与计数 orderNo={} ticketId={}", orderNo, record.getTicketId());
    }

    private void rollbackRedis(SeckillRecord record) {
        long ttl = MIN_KEY_TTL_SECONDS;
        try {
            SeckillActivity activity = activityService.require(record.getActivityId());
            ttl = Math.max(MIN_KEY_TTL_SECONDS,
                    Duration.between(LocalDateTime.now(), activity.getEndTime()).toSeconds()
                            + Duration.ofDays(1).toSeconds());
        } catch (RuntimeException ex) {
            log.debug("回补时读取活动失败，使用默认 TTL activityId={}", record.getActivityId());
        }
        stockRedis.rollback(record.getActivitySkuId(), record.getUserId(), record.getQuantity(), ttl);
    }

    private SeckillResultView view(SeckillRecord record) {
        String message = record.getFailReason() == null ? null
                : MESSAGES.getOrDefault(record.getFailReason(), "抢购失败，请稍后重试");
        return new SeckillResultView(record.getTicketId(), record.getStatus(), record.getOrderNo(),
                record.getFailReason(), message);
    }
}
