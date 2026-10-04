package com.harriol.baiyishop.product.dto;

import com.harriol.baiyishop.product.entity.ParamItem;

/** 参数项响应。 */
public record ParamItemResponse(Long id, Long templateId, String name, String unit, Integer sort) {

    public static ParamItemResponse from(ParamItem item) {
        return new ParamItemResponse(item.getId(), item.getTemplateId(), item.getName(),
                item.getUnit(), item.getSort());
    }
}