package com.harriol.baiyishop.inventory.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.inventory.dto.InventoryFlowItem;
import com.harriol.baiyishop.inventory.dto.InventoryItem;
import com.harriol.baiyishop.inventory.dto.StockAlertItem;
import com.harriol.baiyishop.inventory.dto.StockAvailable;
import com.harriol.baiyishop.inventory.dto.StockItem;
import com.harriol.baiyishop.inventory.dto.StockOpResult;
import com.harriol.baiyishop.inventory.entity.Inventory;
import com.harriol.baiyishop.inventory.entity.InventoryFlow;
import com.harriol.baiyishop.inventory.entity.StockAlert;
import com.harriol.baiyishop.inventory.mapper.InventoryFlowMapper;
import com.harriol.baiyishop.inventory.mapper.InventoryMapper;
import com.harriol.baiyishop.inventory.mapper.StockAlertMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 库存服务（REQ-501 ~ REQ-503、REQ-505）。
 *
 * <p>三条贯穿性的设计：
 * <ol>
 *   <li><b>并发安全</b>：所有增减都用条件更新（WHERE available >= n / locked >= n），
 *       靠影响行数判定成败，不做「先查后改」</li>
 *   <li><b>幂等</b>：每次操作写一条 inventory_flow，biz_key 唯一；重复请求命中唯一冲突后
 *       按「已处理」返回成功语义，不重复扣减也不报错（架构 6.2）</li>
 *   <li><b>留痕</b>：流水记录变更前后的可售与锁定值，加上原因与操作人（REQ-501）</li>
 * </ol>
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private static final int DEFAULT_WARN_THRESHOLD = 10;

    private final InventoryMapper inventoryMapper;
    private final InventoryFlowMapper flowMapper;
    private final StockAlertMapper alertMapper;

    public InventoryService(InventoryMapper inventoryMapper,
                            InventoryFlowMapper flowMapper,
                            StockAlertMapper alertMapper) {
        this.inventoryMapper = inventoryMapper;
        this.flowMapper = flowMapper;
        this.alertMapper = alertMapper;
    }

    // ==================== 内部接口：下单 / 支付 / 取消 ====================

    /** 下单锁定：可售 → 锁定（REQ-502）。任一条库存不足则整体失败并回滚，不产生部分锁定 */
    @Transactional
    public StockOpResult lock(String orderNo, Long productId, List<StockItem> items) {
        return applyOperation(orderNo, InventoryFlow.TYPE_LOCK, productId, items);
    }

    /** 支付成功扣减：锁定库存真正出库（REQ-503） */
    @Transactional
    public StockOpResult deduct(String orderNo, Long productId, List<StockItem> items) {
        return applyOperation(orderNo, InventoryFlow.TYPE_DEDUCT, productId, items);
    }

    /** 取消 / 超时释放：锁定 → 可售（REQ-503、REQ-705） */
    @Transactional
    public StockOpResult release(String orderNo, Long productId, List<StockItem> items) {
        return applyOperation(orderNo, InventoryFlow.TYPE_UNLOCK, productId, items);
    }

    /**
     * 三种操作的公共骨架：幂等 → 条件更新 → 写流水 → 联动预警。
     * <p>先按 biz_key 查一次做快速幂等；随后的插入仍可能撞唯一索引（并发重复请求），
     * 在调用处捕获 DuplicateKeyException 兜底。
     */
    private StockOpResult applyOperation(String orderNo, String type, Long productId, List<StockItem> items) {
        String bizKey = orderNo + ":" + type;
        if (flowMapper.selectOne(Wrappers.<InventoryFlow>lambdaQuery()
                .eq(InventoryFlow::getBizKey, bizKey)) != null) {
            log.info("库存操作幂等命中 bizKey={}", bizKey);
            return StockOpResult.processed();
        }

        List<Long> failed = new ArrayList<>();
        for (StockItem item : items) {
            Inventory inventory = getOrCreate(item.skuId(), productId);
            int rows = switch (type) {
                case InventoryFlow.TYPE_LOCK -> inventoryMapper.lock(item.skuId(), item.quantity());
                case InventoryFlow.TYPE_DEDUCT -> inventoryMapper.deduct(item.skuId(), item.quantity());
                default -> inventoryMapper.release(item.skuId(), item.quantity());
            };
            if (rows == 0) {
                failed.add(item.skuId());
                continue;
            }
            // 更新是原子的，update 后读到的就是权威值，before 由 after 反推，避免读旧快照
            Inventory after = inventoryMapper.selectById(inventory.getId());
            writeFlow(bizKey, item.skuId(), type, item.quantity(), after, type);
            refreshAlert(after);
        }

        if (!failed.isEmpty()) {
            // 抛出后事务回滚，之前已锁定的 SKU 一并还原
            throw new BizException(ErrorCode.INSUFFICIENT_STOCK,
                    "库存不足，无法完成操作，SKU：" + failed);
        }
        return StockOpResult.ok();
    }

    // ==================== 后台：查询 / 调整 / 预警 ====================

    public PageResult<InventoryItem> page(long page, long size, Long skuId, Long productId, Boolean onlyAlert) {
        List<Long> alertSkuIds = alertMapper.selectList(Wrappers.<StockAlert>lambdaQuery()
                        .eq(StockAlert::getStatus, StockAlert.STATUS_OPEN))
                .stream().map(StockAlert::getSkuId).toList();

        Page<Inventory> pager = new Page<>(page, size);
        var query = Wrappers.<Inventory>lambdaQuery()
                .eq(skuId != null, Inventory::getSkuId, skuId)
                .eq(productId != null, Inventory::getProductId, productId)
                .orderByAsc(Inventory::getSkuId);
        if (Boolean.TRUE.equals(onlyAlert)) {
            if (alertSkuIds.isEmpty()) {
                return PageResult.empty(page, size);
            }
            query.in(Inventory::getSkuId, alertSkuIds);
        }
        Page<Inventory> result = inventoryMapper.selectPage(pager, query);

        List<InventoryItem> items = result.getRecords().stream()
                .map(inv -> new InventoryItem(inv.getSkuId(), inv.getProductId(), inv.getAvailable(),
                        inv.getLocked(), inv.getWarnThreshold(),
                        alertSkuIds.contains(inv.getSkuId()), inv.getUpdatedAt()))
                .toList();
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), items);
    }

    public PageResult<InventoryFlowItem> flows(long page, long size, Long skuId, String type) {
        Page<InventoryFlow> pager = new Page<>(page, size);
        Page<InventoryFlow> result = flowMapper.selectPage(pager, Wrappers.<InventoryFlow>lambdaQuery()
                .eq(skuId != null, InventoryFlow::getSkuId, skuId)
                .eq(StringUtils.hasText(type), InventoryFlow::getType, type)
                .orderByDesc(InventoryFlow::getId));
        List<InventoryFlowItem> items = result.getRecords().stream()
                .map(f -> new InventoryFlowItem(f.getId(), f.getBizKey(), f.getSkuId(), f.getType(),
                        f.getQuantity(), f.getBeforeAvailable(), f.getAfterAvailable(),
                        f.getBeforeLocked(), f.getAfterLocked(), f.getReason(),
                        f.getOperatorType(), f.getOperatorId(), f.getCreatedAt()))
                .toList();
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), items);
    }

    /** 后台调整库存（REQ-501）：正数补货、负数减库，调整后不能为负 */
    @Transactional
    public InventoryItem adjust(Long skuId, Long productId, int delta, String reason, Long operatorId) {
        if (delta == 0) {
            throw new BizException(ErrorCode.INVALID_STOCK_ADJUSTMENT);
        }
        Inventory inventory = getOrCreate(skuId, productId);
        int rows = delta > 0
                ? inventoryMapper.increase(skuId, delta)
                : inventoryMapper.decrease(skuId, -delta);
        if (rows == 0) {
            throw new BizException(ErrorCode.INVALID_STOCK_ADJUSTMENT, "调整后库存不能为负");
        }
        Inventory after = inventoryMapper.selectById(inventory.getId());
        writeAdjustFlow("ADJUST:" + UUID.randomUUID(), skuId, delta, after, reason, operatorId);
        refreshAlert(after);
        log.info("后台调整库存 skuId={} delta={} reason={} 结果可售={}", skuId, delta, reason, after.getAvailable());
        return new InventoryItem(after.getSkuId(), after.getProductId(), after.getAvailable(),
                after.getLocked(), after.getWarnThreshold(), after.getAvailable() <= after.getWarnThreshold(),
                after.getUpdatedAt());
    }

    public List<StockAlertItem> alerts(String status) {
        return alertMapper.selectList(Wrappers.<StockAlert>lambdaQuery()
                        .eq(StringUtils.hasText(status), StockAlert::getStatus, status)
                        .orderByDesc(StockAlert::getUpdatedAt))
                .stream()
                .map(a -> new StockAlertItem(a.getId(), a.getSkuId(), a.getProductId(), a.getCurrentStock(),
                        a.getThreshold(), a.getStatus(), a.getHandledAt(), a.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public void closeAlert(Long alertId) {
        StockAlert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw new BizException(ErrorCode.INVENTORY_NOT_FOUND, "预警记录不存在");
        }
        alert.setStatus(StockAlert.STATUS_CLOSED);
        alert.setHandledAt(LocalDateTime.now());
        alertMapper.updateById(alert);
    }

    // ==================== 只读 ====================

    public StockAvailable available(Long skuId) {
        Inventory inventory = inventoryMapper.selectOne(
                Wrappers.<Inventory>lambdaQuery().eq(Inventory::getSkuId, skuId));
        // 没有记录时返回 null，而不是 0 —— 前端需要区分「没配库存」和「库存为 0」
        return new StockAvailable(skuId, inventory == null ? null : inventory.getAvailable());
    }

    public List<StockAvailable> availableBatch(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        return skuIds.stream().map(this::available).toList();
    }

    // ==================== 内部工具 ====================

    /**
     * 取库存记录，没有就建一条零库存的。
     * <p>这样「商品还没配库存」的情况下单会得到明确的「库存不足」，
     * 而不是含义模糊的「库存记录不存在」。
     */
    private Inventory getOrCreate(Long skuId, Long productId) {
        Inventory inventory = inventoryMapper.selectOne(
                Wrappers.<Inventory>lambdaQuery().eq(Inventory::getSkuId, skuId));
        if (inventory != null) {
            return inventory;
        }
        Inventory created = new Inventory();
        created.setSkuId(skuId);
        created.setProductId(productId == null ? 0L : productId);
        created.setAvailable(0);
        created.setLocked(0);
        created.setWarnThreshold(DEFAULT_WARN_THRESHOLD);
        created.setVersion(0);
        created.setUpdatedAt(LocalDateTime.now());
        try {
            inventoryMapper.insert(created);
        } catch (DuplicateKeyException ex) {
            // 并发生成同一条记录时以已有记录为准
            return inventoryMapper.selectOne(Wrappers.<Inventory>lambdaQuery().eq(Inventory::getSkuId, skuId));
        }
        return created;
    }

    /** 写流水。before 由 after 与本次变更量反推，避免读到事务内的旧快照 */
    private void writeFlow(String bizKey, Long skuId, String type, int quantity,
                           Inventory after, String changeKind) {
        writeFlow(bizKey, skuId, type, quantity, after, changeKind, null, null);
    }

    /**
     * 后台调整的流水。
     * <p>单独一个方法是因为 ADJUST 的 before 要用**带符号**的 delta 反推
     * （after - delta），通用方法只拿到绝对值推不出来。
     */
    private void writeAdjustFlow(String bizKey, Long skuId, int signedDelta,
                                 Inventory after, String reason, Long operatorId) {
        InventoryFlow flow = new InventoryFlow();
        flow.setBizKey(bizKey);
        flow.setSkuId(skuId);
        flow.setType(InventoryFlow.TYPE_ADJUST);
        flow.setQuantity(Math.abs(signedDelta));
        flow.setAfterAvailable(after.getAvailable());
        flow.setAfterLocked(after.getLocked());
        flow.setBeforeAvailable(after.getAvailable() - signedDelta);
        flow.setBeforeLocked(after.getLocked());
        flow.setReason(reason);
        flow.setOperatorType(operatorId == null ? "SYSTEM" : "ADMIN");
        flow.setOperatorId(operatorId);
        flow.setCreatedAt(LocalDateTime.now());
        insertFlow(flow);
    }

    /** 插入流水并吞掉幂等冲突：唯一索引拦住重复请求时按「已处理」处理 */
    private void insertFlow(InventoryFlow flow) {
        try {
            flowMapper.insert(flow);
        } catch (DuplicateKeyException ex) {
            log.info("库存流水幂等命中 bizKey={}", flow.getBizKey());
        }
    }

    private void writeFlow(String bizKey, Long skuId, String type, int quantity, Inventory after,
                           String changeKind, String reason, Long operatorId) {
        InventoryFlow flow = new InventoryFlow();
        flow.setBizKey(bizKey);
        flow.setSkuId(skuId);
        flow.setType(type);
        flow.setQuantity(quantity);
        flow.setAfterAvailable(after.getAvailable());
        flow.setAfterLocked(after.getLocked());
        switch (changeKind) {
            case InventoryFlow.TYPE_LOCK -> {
                flow.setBeforeAvailable(after.getAvailable() + quantity);
                flow.setBeforeLocked(after.getLocked() - quantity);
            }
            case InventoryFlow.TYPE_DEDUCT -> {
                flow.setBeforeAvailable(after.getAvailable());
                flow.setBeforeLocked(after.getLocked() + quantity);
            }
            case InventoryFlow.TYPE_UNLOCK -> {
                flow.setBeforeAvailable(after.getAvailable() - quantity);
                flow.setBeforeLocked(after.getLocked() + quantity);
            }
            default -> throw new IllegalStateException("未支持的库存操作类型: " + changeKind);
        }
        flow.setReason(reason);
        flow.setOperatorType(operatorId == null ? "SYSTEM" : "ADMIN");
        flow.setOperatorId(operatorId);
        flow.setCreatedAt(LocalDateTime.now());
        insertFlow(flow);
    }

    /**
     * 库存变更后刷新预警（REQ-505）。
     * <p>uk_sku_open(sku_id, status) 保证每个 SKU 最多一条 OPEN 与一条 CLOSED；
     * 低于等于阈值就复用 / 生成 OPEN，回补上来就把 OPEN 置为 CLOSED。
     */
    private void refreshAlert(Inventory inventory) {
        StockAlert open = alertMapper.selectOne(Wrappers.<StockAlert>lambdaQuery()
                .eq(StockAlert::getSkuId, inventory.getSkuId())
                .eq(StockAlert::getStatus, StockAlert.STATUS_OPEN));
        boolean low = inventory.getAvailable() <= inventory.getWarnThreshold();
        if (low) {
            if (open == null) {
                StockAlert alert = new StockAlert();
                alert.setSkuId(inventory.getSkuId());
                alert.setProductId(inventory.getProductId());
                alert.setCurrentStock(inventory.getAvailable());
                alert.setThreshold(inventory.getWarnThreshold());
                alert.setStatus(StockAlert.STATUS_OPEN);
                alert.setUpdatedAt(LocalDateTime.now());
                alertMapper.insert(alert);
            } else {
                open.setCurrentStock(inventory.getAvailable());
                open.setThreshold(inventory.getWarnThreshold());
                open.setUpdatedAt(LocalDateTime.now());
                alertMapper.updateById(open);
            }
        } else if (open != null) {
            open.setStatus(StockAlert.STATUS_CLOSED);
            open.setHandledAt(LocalDateTime.now());
            open.setUpdatedAt(LocalDateTime.now());
            alertMapper.updateById(open);
        }
    }
}