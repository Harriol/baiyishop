package com.harriol.baiyishop.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新增 / 修改品牌请求（REQ-202）。 */
public record BrandRequest(
        @NotBlank(message = "请填写品牌名称") @Size(max = 50, message = "品牌名称最长 50 个字符")
        String name,

        String logo,

        @Size(max = 500, message = "品牌介绍最长 500 个字符")
        String description,

        Integer sort,

        Boolean enabled) {
}