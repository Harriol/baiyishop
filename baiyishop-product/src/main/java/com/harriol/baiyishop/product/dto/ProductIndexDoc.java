package com.harriol.baiyishop.product.dto;

import java.time.LocalDateTime;

/**
 * 商品搜索索引文档（REQ-302，docs/architecture.md 5.5）。
 * <p>search-service 的唯一数据来源，字段与 ES 索引 {@code baiyishop_product} 一一对应：
 * 关键词命中 name，categoryPath 支撑「按分类含子分类」筛选，sales 与 onSaleTime 支撑综合排序打分。
 * <p>price 与 sales 是 product 表的展示用冗余（分），权威值分别在 product_sku.price 与订单统计。
 * <p>下架商品**照样进索引**（status=OFF_SALE），由搜索侧过滤；
 * 这样重新上架时不必等索引重建就能立刻被搜到。
 */
public record ProductIndexDoc(
        Long id,
        String name,
        String mainImage,
        Long price,
        Integer sales,
        Long categoryId,
        String categoryPath,
        String categoryName,
        Long brandId,
        String brandName,
        String status,
        LocalDateTime onSaleTime,
        LocalDateTime updatedAt) {
}
