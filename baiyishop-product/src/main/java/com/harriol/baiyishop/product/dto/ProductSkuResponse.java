package com.harriol.baiyishop.product.dto;

import com.harriol.baiyishop.product.entity.ProductSku;

/** SKU 出参（价格以「分」返回，前端做展示换算）。 */
public record ProductSkuResponse(Long id, String skuCode, String specName, Long price,
                                 String image, Integer sort, boolean enabled) {

    public static ProductSkuResponse from(ProductSku sku) {
        return new ProductSkuResponse(sku.getId(), sku.getSkuCode(), sku.getSpecName(), sku.getPrice(),
                sku.getImage(), sku.getSort(), sku.getStatus() != null && sku.getStatus() == 1);
    }
}