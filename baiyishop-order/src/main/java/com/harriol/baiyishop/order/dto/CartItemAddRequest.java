package com.harriol.baiyishop.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 加入购物车（REQ-601）。 */
public record CartItemAddRequest(
        @NotNull(message = "请选择商品规格") Long skuId,
        @NotNull(message = "请填写数量") @Min(value = 1, message = "数量至少为 1") Integer quantity) {
}
