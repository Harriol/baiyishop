package com.harriol.baiyishop.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 后台调整库存（REQ-501）。delta 为正数补货、负数减库；调整后不能为负。
 */
public record InventoryAdjustRequest(
        @NotNull(message = "请填写调整数量") Integer delta,

        @NotBlank(message = "请填写调整原因") @Size(max = 200, message = "原因最长 200 个字符")
        String reason) {
}