package com.harriol.baiyishop.product.dto;

import com.harriol.baiyishop.product.entity.Category;

import java.util.ArrayList;
import java.util.List;

/** 分类节点，children 用于拼三级分类树。 */
public record CategoryResponse(Long id, Long parentId, String name, Integer level, String path,
                               String icon, Integer sort, boolean visible, List<CategoryResponse> children) {

    public static CategoryResponse of(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getParentId(),
                category.getName(),
                category.getLevel(),
                category.getPath(),
                category.getIcon(),
                category.getSort(),
                category.getVisible() != null && category.getVisible() == 1,
                new ArrayList<>());
    }
}