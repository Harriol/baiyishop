package com.harriol.baiyishop.order.dto;

/**
 * 待下单的一行（解析后的中间结构）。
 *
 * @param cartItemId 来自购物车时有值；立即购买为 null（下单成功后按它清理购物车）
 */
public record OrderLine(SkuSnapshot sku, int quantity, Long cartItemId) {

    public long amount() {
        return sku.price() * quantity;
    }
}
