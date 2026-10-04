package com.harriol.baiyishop.product.dto;

import java.time.LocalDateTime;

/** 后台商品列表项。分类名与品牌名由服务层批量补齐，避免逐条查库。 */
public record ProductAdminItem(Long id, String name, String mainImage, Long categoryId, String categoryName,
                               Long brandId, String brandName, Long minPrice, Integer sales,
                               String status, LocalDateTime onSaleTime) {
}