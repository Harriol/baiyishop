package com.harriol.baiyishop.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增 / 修改分类请求（REQ-201）。
 *
 * @param parentId 父分类 ID，0 表示顶级
 */
public record CategoryRequest(
        Long parentId,

        @NotBlank(message = "请填写分类名称") @Size(max = 50, message = "分类名称最长 50 个字符")
        String name,

        String icon,

        Integer sort,

        Boolean visible) {
}