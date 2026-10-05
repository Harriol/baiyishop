package com.harriol.baiyishop.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 后台发货（REQ-708）：只记录运单号，不追踪物流轨迹。 */
public record ShipRequest(
        @NotBlank(message = "请填写运单号") @Size(max = 64, message = "运单号最长 64 个字符") String trackingNo) {
}
