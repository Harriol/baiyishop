package com.harriol.baiyishop.order.dto;

/** inventory-service 的只读库存视图（docs/api.md 第 6 章）。 */
public record StockAvailable(Long skuId, Integer available) {
}
