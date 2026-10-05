package com.harriol.baiyishop.order.dto;

import jakarta.validation.constraints.NotNull;

/** 全选 / 取消全选（REQ-601）。 */
public record CartCheckedRequest(@NotNull(message = "请指定勾选状态") Boolean checked) {
}
