package com.harriol.baiyishop.order.dto;

import java.util.List;

/**
 * 库存操作请求（锁定 / 扣减 / 释放）。
 * <p>{@code orderNo} 同时是幂等键：全局事务重试或消息重投都不会重复扣减（REQ-503）。
 */
public record InventoryOpRequest(String orderNo, Long productId, List<InventoryOpItem> items) {
}
