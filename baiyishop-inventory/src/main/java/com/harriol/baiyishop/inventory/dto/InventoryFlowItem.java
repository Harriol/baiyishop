package com.harriol.baiyishop.inventory.dto;

import java.time.LocalDateTime;

/** 库存流水项（REQ-501）。 */
public record InventoryFlowItem(Long id, String bizKey, Long skuId, String type, Integer quantity,
                                Integer beforeAvailable, Integer afterAvailable,
                                Integer beforeLocked, Integer afterLocked,
                                String reason, String operatorType, Long operatorId,
                                LocalDateTime createdAt) {
}