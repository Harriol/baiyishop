package com.harriol.baiyishop.order.dto;

/**
 * 结算页的一行商品。
 *
 * @param valid         失效行不参与合计，前端置灰并展示 invalidReason（REQ-602）
 * @param invalidReason 失效原因；有效时为 null
 */
public record ItemPreview(
        Long skuId,
        Long productId,
        String name,
        String image,
        Long price,
        Integer quantity,
        Long totalAmount,
        boolean valid,
        String invalidReason) {
}
