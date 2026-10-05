package com.harriol.baiyishop.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 后台添加订单备注（REQ-708）。 */
public record OrderNoteRequest(
        @NotBlank(message = "请填写备注内容") @Size(max = 500, message = "备注最长 500 个字符") String content) {
}
