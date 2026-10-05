package com.harriol.baiyishop.order.dto;

/**
 * product-service 的 SKU 快照视图（docs/api.md 第 6 章）。
 * <p>字段与 product 模块的 {@code ProductSkuSnapshot} 对齐，跨服务通过 HTTP 契约耦合。
 */
public record SkuSnapshot(
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
