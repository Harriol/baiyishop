package com.harriol.baiyishop.inventory.dto;

import java.util.List;

/**
 * 库存操作结果。
 *
 * @param alreadyProcessed 是否为重复请求（幂等命中，未重复扣减）
 * @param failedSkuIds     失败时库存不足的 SKU 列表
 */
public record StockOpResult(boolean success, boolean alreadyProcessed, List<Long> failedSkuIds) {

    public static StockOpResult ok() {
        return new StockOpResult(true, false, List.of());
    }

    public static StockOpResult processed() {
        return new StockOpResult(true, true, List.of());
    }

    public static StockOpResult failed(List<Long> failedSkuIds) {
        return new StockOpResult(false, false, failedSkuIds);
    }
}