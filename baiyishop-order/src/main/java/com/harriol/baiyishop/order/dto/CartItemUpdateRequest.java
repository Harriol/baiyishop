package com.harriol.baiyishop.order.dto;

import jakarta.validation.constraints.Min;

/**
 * 修改购物车条目：数量与勾选状态都是可选的（REQ-601）。
 * <p>只传 checked 就是勾选 / 取消勾选，只传 quantity 就是改数量。
 */
public record CartItemUpdateRequest(
        @Min(value = 1, message = "数量至少为 1") Integer quantity,
        Boolean checked) {
}
