package com.harriol.baiyishop.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 库存操作中的单个 SKU 项。 */
public record StockItem(
        @NotNull(message = "缺少 skuId") Long skuId,

        @NotNull(message = "缺少数量") @Min(value = 1, message = "数量必须大于 0") Integer quantity) {
}