package com.harriol.baiyishop.product.dto;

import com.harriol.baiyishop.product.entity.Brand;

/** 品牌响应。 */
public record BrandResponse(Long id, String name, String logo, String description, Integer sort, boolean enabled) {

    public static BrandResponse from(Brand brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getName(),
                brand.getLogo(),
                brand.getDescription(),
                brand.getSort(),
                brand.getEnabled() != null && brand.getEnabled() == 1);
    }
}