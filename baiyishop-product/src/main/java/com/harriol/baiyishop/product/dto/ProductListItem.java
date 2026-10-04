package com.harriol.baiyishop.product.dto;

/** 前台商品列表项。 */
public record ProductListItem(Long id, String name, String mainImage, Long price,
                              Integer sales, Long categoryId, Long brandId, String brandName) {
}