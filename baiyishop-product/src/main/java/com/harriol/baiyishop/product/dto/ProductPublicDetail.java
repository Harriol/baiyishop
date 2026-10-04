package com.harriol.baiyishop.product.dto;

import java.util.List;

/**
 * 前台商品详情（REQ-206）。
 *
 * @param status    ON_SALE / OFF_SALE；已下架时前端据此展示提示而不是报错
 * @param available 可售库存。**当前恒为 null** —— 库存权威在 inventory-service，
 *                  待该服务就绪后由 product-service 只读接入（docs/architecture.md 4.3）
 * @param categoryPath 物化路径，供前端做分类面包屑
 */
public record ProductPublicDetail(Long id, String name, Long categoryId, String categoryName,
                                  String categoryPath, Long brandId, String brandName,
                                  String mainImage, List<String> images, String detail,
                                  String status, Long minPrice, Integer sales,
                                  Integer available, List<ProductSkuResponse> skus,
                                  List<ProductParamResponse> params) {
}