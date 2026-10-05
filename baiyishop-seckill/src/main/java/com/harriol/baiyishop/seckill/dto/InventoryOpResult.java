package com.harriol.baiyishop.seckill.dto;

import java.util.List;

/** inventory 的库存操作结果（与 StockOpResult 对齐）。 */
public record InventoryOpResult(boolean success, boolean alreadyProcessed, List<Long> failedSkuIds) {
}
