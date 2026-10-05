package com.harriol.baiyishop.seckill.dto;

/** product-service 的 SKU 快照视图（docs/api.md 第 6 章）。 */
public record SkuSnapshot(Long skuId, String skuCode, String specName, Long price, String image,
                          boolean skuEnabled, Long productId, String productName, String productImage,
                          String productStatus) {

    public boolean sellable() {
        return skuEnabled && "ON_SALE".equals(productStatus);
    }
}
