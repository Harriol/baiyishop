package com.harriol.baiyishop.product.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品详情（后台编辑与前台详情共用）。
 *
 * @param status    ON_SALE / OFF_SALE
 * @param minPrice  默认 SKU 价格（分）
 */
public record ProductDetailResponse(Long id, String name, Long categoryId, String categoryName,
                                    Long brandId, String brandName, String mainImage, List<String> images,
                                    String detail, String status, Long minPrice, Integer sales,
                                    LocalDateTime onSaleTime, List<ProductSkuResponse> skus,
                                    List<ProductParamResponse> params) {
}