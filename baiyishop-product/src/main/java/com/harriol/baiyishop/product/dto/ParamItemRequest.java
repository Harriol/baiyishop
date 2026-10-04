package com.harriol.baiyishop.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 新增 / 修改参数项（REQ-204）。新增时必须指定所属模板。 */
public record ParamItemRequest(
        Long templateId,

        @NotBlank(message = "请填写参数项名称") @Size(max = 50, message = "参数项名称最长 50 个字符")
        String name,

        @Size(max = 20, message = "单位最长 20 个字符") String unit,

        Integer sort) {
}