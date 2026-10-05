package com.harriol.baiyishop.order.dto;

/**
 * 购物车条目视图（docs/api.md 4.6）。
 *
 * @param invalid       true 表示失效（商品已删除 / 下架 / 售罄 / 库存不足），前端置灰不可选
 * @param invalidReason 失效原因，直接展示给用户
 */
public record CartItemView(
        Long id,
        Long skuId,
        Long productId,
        String name,
        String image,
        Long price,
        Integer quantity,
        boolean checked,
        boolean invalid,
        String invalidReason) {
}
