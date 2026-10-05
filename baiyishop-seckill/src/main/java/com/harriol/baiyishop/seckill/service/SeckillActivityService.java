package com.harriol.baiyishop.seckill.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.seckill.client.InventoryClient;
import com.harriol.baiyishop.seckill.client.ProductClient;
import com.harriol.baiyishop.seckill.dto.ActivityRequest;
import com.harriol.baiyishop.seckill.dto.ActivitySkuRequest;
import com.harriol.baiyishop.seckill.dto.ActivitySkuView;
import com.harriol.baiyishop.seckill.dto.ActivityView;
import com.harriol.baiyishop.seckill.dto.AllocateStockRequest;
import com.harriol.baiyishop.seckill.dto.ReturnStockRequest;
import com.harriol.baiyishop.seckill.dto.SeckillPoolView;
import com.harriol.baiyishop.seckill.dto.SkuSnapshot;
import com.harriol.baiyishop.seckill.entity.SeckillActivity;
import com.harriol.baiyishop.seckill.entity.SeckillActivitySku;
import com.harriol.baiyishop.seckill.mapper.SeckillActivityMapper;
import com.harriol.baiyishop.seckill.mapper.SeckillActivitySkuMapper;
import com.harriol.baiyishop.seckill.redis.SeckillStockRedis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 秒杀活动管理（REQ-901、REQ-902、REQ-504）。
 * <p>三条约定：
 * <ol>
 *   <li>**划拨在创建时完成**：每个活动 SKU 通过 inventory 的划拨接口把普通库存搬进秒杀池，
 *       业务上「活动商品已经备好货」。跨服务没有全局事务，故失败时手工补偿回补已划拨的部分</li>
 *   <li>**结束 / 删除自动回补未售出**：UNSOLD 模式以池内剩余为准还回普通库存（REQ-504）</li>
 *   <li>**状态以服务端时间判定**：创建时算一次，之后由定时任务推进（NOT_STARTED → RUNNING → ENDED），
 *       购买时 Redis Lua 还会再校验一次时间，状态滞后不会导致超卖</li>
 * </ol>
 */
@Service
public class SeckillActivityService {

    private static final Logger log = LoggerFactory.getLogger(SeckillActivityService.class);

    /** 活动缓存 TTL：比活动时长多留 1 天，保证跨天活动不会中途失效 */
    private static final Duration CACHE_EXTRA = Duration.ofDays(1);

    private final SeckillActivityMapper activityMapper;
    private final SeckillActivitySkuMapper activitySkuMapper;
    private final InventoryClient inventoryClient;
    private final ProductClient productClient;
    private final SeckillStockRedis stockRedis;

    public SeckillActivityService(SeckillActivityMapper activityMapper,
                                  SeckillActivitySkuMapper activitySkuMapper,
                                  InventoryClient inventoryClient,
                                  ProductClient productClient,
                                  SeckillStockRedis stockRedis) {
        this.activityMapper = activityMapper;
        this.activitySkuMapper = activitySkuMapper;
        this.inventoryClient = inventoryClient;
        this.productClient = productClient;
        this.stockRedis = stockRedis;
    }

    // ==================== 后台：增删改查（REQ-901） ====================

    @Transactional(rollbackFor = Exception.class)
    public ActivityView create(long adminId, ActivityRequest request) {
        validate(request);
        SeckillActivity activity = new SeckillActivity();
        activity.setName(request.name().trim());
        activity.setStartTime(request.startTime());
        activity.setEndTime(request.endTime());
        activity.setStatus(statusOf(request.startTime(), request.endTime(), LocalDateTime.now()));
        activity.setCreatedBy(adminId);
        activityMapper.insert(activity);

        List<SeckillActivitySku> saved = new ArrayList<>();
        List<Long> allocated = new ArrayList<>();
        try {
            int sort = 0;
            for (ActivitySkuRequest skuRequest : request.skus()) {
                SkuSnapshot sku = productClient.sku(skuRequest.skuId());
                if (!sku.sellable()) {
                    throw new BizException(ErrorCode.SECKILL_SKU_NOT_ON_SALE, "SKU " + skuRequest.skuId() + " 未上架");
                }
                if (skuRequest.seckillPrice() >= sku.price()) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "秒杀价应低于原价：SKU " + skuRequest.skuId());
                }
                SeckillActivitySku entity = new SeckillActivitySku();
                entity.setActivityId(activity.getId());
                entity.setSkuId(sku.skuId());
                entity.setProductId(sku.productId());
                entity.setSeckillPrice(skuRequest.seckillPrice());
                entity.setOriginalPrice(sku.price());
                entity.setAllocStock(skuRequest.allocStock());
                entity.setLimitPerUser(skuRequest.limitPerUser() == null ? 1 : skuRequest.limitPerUser());
                entity.setSort(skuRequest.sort() == null ? sort : skuRequest.sort());
                activitySkuMapper.insert(entity);
                saved.add(entity);

                // 划拨即扣减普通库存（REQ-504）；batchNo 保证重试幂等
                inventoryClient.allocate(new AllocateStockRequest(activity.getId(), entity.getId(), entity.getSkuId(),
                        entity.getProductId(), entity.getAllocStock(), allocateBatchNo(activity.getId(), entity.getId())));
                allocated.add(entity.getId());
            }
        } catch (RuntimeException ex) {
            // 秒杀链路不进 Seata（ADR-008 边界）：手工把已划拨的先还回去，再让本地事务回滚
            compensatedReturn(activity.getId(), allocated);
            throw ex;
        }
        warmUp(activity, saved);
        log.info("创建秒杀活动 id={} name={} skus={} 状态={}", activity.getId(), activity.getName(),
                saved.size(), activity.getStatus());
        return view(activity, saved);
    }

    /**
     * 修改活动：只允许**未开始 / 草稿**的活动，且仅支持改名称与起止时间。
     * <p>商品明细与划拨量不做在线修改：已经划拨的库存、Redis 里的预扣量、用户已购计数三者要同时换算，
     * 出错面远大于收益 —— 要调商品就结束或删除后重建（在 docs/api.md 5.5 里记为已知限制）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ActivityView update(long id, ActivityRequest request) {
        SeckillActivity activity = require(id);
        if (!SeckillActivity.STATUS_NOT_STARTED.equals(activity.getStatus())
                && !SeckillActivity.STATUS_DRAFT.equals(activity.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只有未开始的活动可以修改");
        }
        if (!StringUtils.hasText(request.name()) || request.startTime() == null || request.endTime() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写活动名称与起止时间");
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }
        activity.setName(request.name().trim());
        activity.setStartTime(request.startTime());
        activity.setEndTime(request.endTime());
        activity.setStatus(statusOf(request.startTime(), request.endTime(), LocalDateTime.now()));
        activityMapper.updateById(activity);

        List<SeckillActivitySku> skus = skusOf(id);
        warmUp(activity, skus);
        return view(activity, skus);
    }

    /** 结束活动：未售出的回补普通库存（REQ-504、REQ-902） */
    @Transactional(rollbackFor = Exception.class)
    public void end(long id) {
        SeckillActivity activity = require(id);
        if (SeckillActivity.STATUS_ENDED.equals(activity.getStatus())) {
            return;
        }
        List<SeckillActivitySku> skus = skusOf(id);
        returnUnsold(activity, skus);
        activityMapper.update(null, Wrappers.<SeckillActivity>lambdaUpdate()
                .eq(SeckillActivity::getId, id)
                .set(SeckillActivity::getStatus, SeckillActivity.STATUS_ENDED));
        log.info("秒杀活动已结束并回补未售出库存 activityId={}", id);
    }

    /** 删除活动：未开始 / 已结束可删，删除前先回补（REQ-901） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) {
        SeckillActivity activity = require(id);
        if (SeckillActivity.STATUS_RUNNING.equals(activity.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "进行中的活动不能删除，请先结束");
        }
        List<SeckillActivitySku> skus = skusOf(id);
        if (SeckillActivity.STATUS_NOT_STARTED.equals(activity.getStatus())) {
            returnUnsold(activity, skus);
        }
        skus.forEach(sku -> stockRedis.clear(sku.getId()));
        activitySkuMapper.delete(Wrappers.<SeckillActivitySku>lambdaQuery()
                .eq(SeckillActivitySku::getActivityId, id));
        activityMapper.deleteById(id);
        log.info("秒杀活动已删除 activityId={}", id);
    }

    public PageResult<ActivityView> page(long page, long size, String status) {
        Page<SeckillActivity> result = activityMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 50)),
                Wrappers.<SeckillActivity>lambdaQuery()
                        .eq(StringUtils.hasText(status), SeckillActivity::getStatus, status)
                        .orderByDesc(SeckillActivity::getId));
        List<ActivityView> views = result.getRecords().stream()
                .map(activity -> view(activity, skusOf(activity.getId())))
                .toList();
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), views);
    }

    public ActivityView detail(long id) {
        SeckillActivity activity = require(id);
        return view(activity, skusOf(id));
    }

    // ==================== 状态推进与预热 ====================

    /** 定时推进状态：到点开始、过点结束并回补（REQ-902） */
    public void refreshRunningStatus() {
        LocalDateTime now = LocalDateTime.now();
        activityMapper.selectList(Wrappers.<SeckillActivity>lambdaQuery()
                        .eq(SeckillActivity::getStatus, SeckillActivity.STATUS_NOT_STARTED)
                        .le(SeckillActivity::getStartTime, now)
                        .last("LIMIT 50"))
                .forEach(activity -> {
                    activityMapper.update(null, Wrappers.<SeckillActivity>lambdaUpdate()
                            .eq(SeckillActivity::getId, activity.getId())
                            .set(SeckillActivity::getStatus, SeckillActivity.STATUS_RUNNING));
                    warmUp(activity, skusOf(activity.getId()));
                });

        activityMapper.selectList(Wrappers.<SeckillActivity>lambdaQuery()
                        .eq(SeckillActivity::getStatus, SeckillActivity.STATUS_RUNNING)
                        .lt(SeckillActivity::getEndTime, now)
                        .last("LIMIT 50"))
                .forEach(activity -> end(activity.getId()));
    }

    /** 预热：写入活动时间与初始库存，供 Lua 原子判定使用（ADR-008） */
    void warmUp(SeckillActivity activity, List<SeckillActivitySku> skus) {
        if (activity.getStartTime() == null || activity.getEndTime() == null) {
            return;
        }
        long startMs = toEpochMillis(activity.getStartTime());
        long endMs = toEpochMillis(activity.getEndTime());
        Duration ttl = Duration.between(LocalDateTime.now(), activity.getEndTime()).plus(CACHE_EXTRA);
        for (SeckillActivitySku sku : skus) {
            stockRedis.warmUp(activity.getId(), sku.getId(), sku.getAllocStock(), startMs, endMs, ttl);
        }
    }

    SeckillActivity require(long id) {
        SeckillActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BizException(ErrorCode.SECKILL_ACTIVITY_NOT_FOUND);
        }
        return activity;
    }

    List<SeckillActivitySku> skusOf(long activityId) {
        return activitySkuMapper.selectList(Wrappers.<SeckillActivitySku>lambdaQuery()
                .eq(SeckillActivitySku::getActivityId, activityId)
                .orderByAsc(SeckillActivitySku::getSort)
                .orderByAsc(SeckillActivitySku::getId));
    }

    // ==================== 内部方法 ====================

    private void validate(ActivityRequest request) {
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写活动名称");
        }
        if (request.startTime() == null || request.endTime() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写活动起止时间");
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }
        if (request.endTime().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束时间必须晚于当前时间");
        }
        if (CollectionUtils.isEmpty(request.skus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择至少一个秒杀商品");
        }
        for (ActivitySkuRequest sku : request.skus()) {
            if (sku.skuId() == null || sku.seckillPrice() == null || sku.seckillPrice() < 1) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请填写商品与合法秒杀价");
            }
            if (sku.allocStock() == null || sku.allocStock() < 1) {
                throw new BizException(ErrorCode.PARAM_INVALID, "划拨库存必须大于 0");
            }
            if (sku.limitPerUser() != null && sku.limitPerUser() < 1) {
                throw new BizException(ErrorCode.PARAM_INVALID, "每人限购数必须大于 0");
            }
        }
    }

    /** 活动结束时把池内剩余还回普通库存；每个 SKU 一个幂等批次号，重复调用不会重复回补 */
    private void returnUnsold(SeckillActivity activity, List<SeckillActivitySku> skus) {
        for (SeckillActivitySku sku : skus) {
            try {
                inventoryClient.returnStock(new ReturnStockRequest(sku.getId(), ReturnStockRequest.MODE_UNSOLD,
                        null, null, "SEK-END-" + activity.getId()));
            } catch (RuntimeException ex) {
                // 回补失败不阻断活动结束：池内剩余留待对账任务或人工补回
                log.warn("活动未售出库存回补失败 activitySkuId={}", sku.getId(), ex);
            }
        }
    }

    private void compensatedReturn(long activityId, List<Long> activitySkuIds) {
        for (Long activitySkuId : activitySkuIds) {
            try {
                inventoryClient.returnStock(new ReturnStockRequest(activitySkuId, ReturnStockRequest.MODE_UNSOLD,
                        null, null, "SEK-END-" + activityId));
            } catch (RuntimeException ex) {
                log.error("创建活动失败后的补偿回补也失败，需人工核对 activitySkuId={}", activitySkuId, ex);
            }
        }
    }

    private ActivityView view(SeckillActivity activity, List<SeckillActivitySku> skus) {
        List<ActivitySkuView> skuViews = skus.stream().map(this::skuView).toList();
        return new ActivityView(activity.getId(), activity.getName(), activity.getStartTime(),
                activity.getEndTime(), activity.getStatus(), countdownSeconds(activity), skuViews);
    }

    private ActivitySkuView skuView(SeckillActivitySku sku) {
        Integer remaining = null;
        SkuSnapshot snapshot = null;
        try {
            SeckillPoolView pool = inventoryClient.poolOrNull(sku.getId());
            remaining = pool == null ? null : pool.remaining();
            snapshot = productClient.sku(sku.getSkuId());
        } catch (RuntimeException ex) {
            // 展示接口不因依赖抖动报错：剩余量 / 商品名缺失就留空
            log.debug("活动 SKU 展示信息获取失败 activitySkuId={}", sku.getId());
        }
        return new ActivitySkuView(sku.getId(), sku.getSkuId(), sku.getProductId(),
                snapshot == null ? null : snapshot.productName(),
                snapshot == null ? null : snapshot.image(),
                sku.getSeckillPrice(), sku.getOriginalPrice(), sku.getAllocStock(), remaining,
                sku.getLimitPerUser(), sku.getSort());
    }

    private Long countdownSeconds(SeckillActivity activity) {
        LocalDateTime now = LocalDateTime.now();
        if (SeckillActivity.STATUS_NOT_STARTED.equals(activity.getStatus())) {
            return Math.max(0, Duration.between(now, activity.getStartTime()).toSeconds());
        }
        if (SeckillActivity.STATUS_RUNNING.equals(activity.getStatus())) {
            return Math.max(0, Duration.between(now, activity.getEndTime()).toSeconds());
        }
        return 0L;
    }

    private String statusOf(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        if (now.isBefore(start)) {
            return SeckillActivity.STATUS_NOT_STARTED;
        }
        if (now.isAfter(end)) {
            return SeckillActivity.STATUS_ENDED;
        }
        return SeckillActivity.STATUS_RUNNING;
    }

    private String allocateBatchNo(long activityId, long activitySkuId) {
        return "SEK-ALLOC-" + activityId + "-" + activitySkuId;
    }

    private long toEpochMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
