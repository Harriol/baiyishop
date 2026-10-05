package com.harriol.baiyishop.order.dto;

import java.util.List;

/**
 * 提交订单（docs/api.md 4.7、REQ-701）。
 * <p>**不含金额字段**：金额一律由服务端按快照单价重算，避免前端改价。
 */
public record CreateOrderRequest(
        String source,
        List<Long> cartItemIds,
        Long skuId,
        Integer quantity,
        Long addressId,
        String remark) {
}
