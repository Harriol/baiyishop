package com.harriol.baiyishop.product.dto;

import java.util.List;

/** 参数模板响应，items 为该模板下的参数项。 */
public record ParamTemplateResponse(Long id, String name, Integer sort, boolean enabled,
                                    List<ParamItemResponse> items) {
}