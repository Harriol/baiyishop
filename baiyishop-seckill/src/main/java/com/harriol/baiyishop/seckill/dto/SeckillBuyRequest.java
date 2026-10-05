package com.harriol.baiyishop.seckill.dto;

import jakarta.validation.constraints.Min;

/** 抢购请求（REQ-903）：requestId 与 X-Request-Id 一致，用于请求级幂等。 */
public record SeckillBuyRequest(
        @Min(value = 1, message = "抢购数量至少为 1") Integer quantity,
        String requestId) {
}
