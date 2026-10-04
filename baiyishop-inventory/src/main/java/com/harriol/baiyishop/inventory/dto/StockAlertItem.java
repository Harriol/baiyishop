package com.harriol.baiyishop.inventory.dto;

import java.time.LocalDateTime;

/** 库存预警项（REQ-505）。 */
public record StockAlertItem(Long id, Long skuId, Long productId, Integer currentStock,
                             Integer threshold, String status, LocalDateTime handledAt,
                             LocalDateTime updatedAt) {
}