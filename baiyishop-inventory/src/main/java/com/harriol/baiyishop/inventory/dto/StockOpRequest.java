package com.harriol.baiyishop.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 库存操作请求（锁定 / 扣减 / 释放），由 order-service 通过内部接口调用。
 * <p>orderNo 同时是**幂等键**的一部分：同一订单重复调用只会生效一次（REQ-503）。
 */
public record StockOpRequest(
        @NotBlank(message = "缺少 orderNo") String orderNo,

        Long productId,

        @NotEmpty(message = "缺少库存明细") @Valid List<StockItem> items) {
}