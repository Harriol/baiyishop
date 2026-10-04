package com.harriol.baiyishop.search.dto;

/**
 * 搜索结果项（docs/api.md 4.4）。
 * <p>只回列表页需要的字段：跳详情的 id、卡片用的主图与价格、排序维度用的销量与品牌。
 * <p>price 以「分」返回，与 product-service 的商品列表保持一致（前端统一做展示换算）。
 */
public record SearchItem(Long id, String name, String mainImage, Long price, Integer sales,
                         Long categoryId, Long brandId, String brandName) {
}
