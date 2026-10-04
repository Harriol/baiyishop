package com.harriol.baiyishop.product.dto.home;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 金刚区入口项。 */
public record NavItem(
        @NotBlank(message = "请填写入口名称") @Size(max = 50, message = "名称最长 50 个字符") String name,

        String icon,

        Long categoryId,

        Integer sort,

        Boolean enabled) {
}