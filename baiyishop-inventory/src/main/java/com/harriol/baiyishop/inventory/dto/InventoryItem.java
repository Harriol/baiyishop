package com.harriol.baiyishop.inventory.dto;

import java.time.LocalDateTime;

/**
 * 后台库存列表项。
 * <p>商品名称不在这里 —— 商品数据属 product-service 的 schema（ADR-007）。
 * 待 product ↔ inventory 打通后，由 product-service 聚合展示名称；本服务只返回权威库存数字。
 */
public record InventoryItem(Long skuId, Long productId, Integer available, Integer locked,
                            Integer warnThreshold, boolean alerting, LocalDateTime updatedAt) {
}