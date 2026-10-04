package com.harriol.baiyishop.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 商品参数值入参（REQ-204）。
 * <p>参数项来自后台统一维护的模板；同一商品的参数项必须属于**同一个模板**，
 * 否则返回 30009。
 */
public record ProductParamRequest(
        @NotNull(message = "请选择参数项") Long paramItemId,

        @NotBlank(message = "请填写参数值") @Size(max = 200, message = "参数值最长 200 个字符")
        String value) {
}