package com.harriol.baiyishop.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * SKU 入参。本期单规格，一个商品只提交一个默认 SKU。
 * <p>注意：**不含库存**。库存权威在 inventory-service，商品侧不接受库存字段（ADR-004）。
 *
 * @param price 售价，单位「分」
 */
public record ProductSkuRequest(
        @Size(max = 100, message = "规格名最长 100 个字符") String specName,

        @NotNull(message = "请填写 SKU 价格")
        @Min(value = 1, message = "价格必须大于 0")
        Long price,

        String image,

        Integer sort,

        Boolean enabled) {
}