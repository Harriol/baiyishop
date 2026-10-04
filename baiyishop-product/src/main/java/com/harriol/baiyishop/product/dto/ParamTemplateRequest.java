package com.harriol.baiyishop.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新增 / 修改参数模板（REQ-204）。 */
public record ParamTemplateRequest(
        @NotBlank(message = "请填写模板名称") @Size(max = 50, message = "模板名称最长 50 个字符")
        String name,

        Integer sort,

        Boolean enabled) {
}