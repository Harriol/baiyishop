package com.harriol.baiyishop.inventory.dto;

/** 只读库存（前台展示 / 商品详情聚合用）。 */
public record StockAvailable(Long skuId, Integer available) {
}