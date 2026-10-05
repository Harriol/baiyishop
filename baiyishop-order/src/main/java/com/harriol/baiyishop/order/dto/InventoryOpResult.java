package com.harriol.baiyishop.order.dto;

import java.util.List;

/** 库存操作结果（与 inventory 的 StockOpResult 对齐）。 */
public record InventoryOpResult(boolean success, boolean alreadyProcessed, List<Long> failedSkuIds) {
}
