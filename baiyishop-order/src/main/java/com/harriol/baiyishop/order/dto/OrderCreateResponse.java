package com.harriol.baiyishop.order.dto;

import java.time.LocalDateTime;

/** 下单结果（docs/api.md 4.7）。 */
public record OrderCreateResponse(String orderNo, Long payAmount, String status, LocalDateTime timeoutAt) {
}
