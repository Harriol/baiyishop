package com.harriol.baiyishop.search.dto;

import java.time.LocalDateTime;

/**
 * product-service {@code GET /internal/products/{id}/index-doc} 的响应视图。
 * <p>字段与 product 模块的 {@code ProductIndexDoc} 一一对应。**故意不共享同一个类**：
 * 服务间只通过 HTTP 契约耦合，改一处不必重编译另一处（架构 2.6）。
 */
public record IndexDocView(
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
