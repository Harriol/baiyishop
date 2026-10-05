package com.harriol.baiyishop.order.dto;

/** 库存操作明细（与 inventory 的 StockItem 对齐）。 */
public record InventoryOpItem(Long skuId, Integer quantity) {
}
