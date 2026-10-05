package com.harriol.baiyishop.product.dto;

/**
 * SKU 快照，供 order-service 在**下单 / 购物车**时读取（docs/api.md 第 6 章）。
 * <p>把「能否售卖」的判定收在产品侧：下单方只看 {@code sellable}，
 * 不需要自己拼 product.status + sku.status 的规则。
 * <p>价格以「分」返回；image 为 SKU 图，为空时回落到商品主图。
 */
public record ProductSkuSnapshot(
        Long skuId,
        String skuCode,
        String specName,
        Long price,
        String image,
        boolean skuEnabled,
        Long productId,
        String productName,
        String productImage,
        String productStatus) {

    /** 商品上架且 SKU 启用才算可售 */
    public boolean sellable() {
        return skuEnabled && "ON_SALE".equals(productStatus);
    }
}
