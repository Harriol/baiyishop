package com.harriol.baiyishop.product.dto.home;

import com.harriol.baiyishop.product.dto.ProductListItem;

import java.util.List;

/** 前台首页楼层视图：楼层信息 + 已拉取的商品。分类下无商品时 products 为空数组，不报错。 */
public record HomeFloorView(Long id, String title, Long categoryId, String sortField,
                            List<ProductListItem> products) {
}