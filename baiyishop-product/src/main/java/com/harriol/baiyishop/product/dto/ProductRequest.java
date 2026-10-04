package com.harriol.baiyishop.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新增 / 修改商品请求（REQ-203）。
 * <p>skuCode 不在入参里 —— 由服务端按 {productId}-{两位序号} 自动生成（docs/database.md 4.4）。
 * <p>params 里的参数项必须来自同一个模板，否则返回 30009（REQ-204）。
 */
public record ProductRequest(
        @NotBlank(message = "请填写商品名称") @Size(max = 200, message = "商品名称最长 200 个字符")
        String name,

        @NotNull(message = "请选择商品分类") Long categoryId,

        Long brandId,

        @NotBlank(message = "请上传商品主图") String mainImage,

        List<String> images,

        String detail,

        Boolean onSale,

        @Valid List<ProductParamRequest> params,

        @NotEmpty(message = "至少需要一个 SKU") @Valid List<ProductSkuRequest> skus) {
}