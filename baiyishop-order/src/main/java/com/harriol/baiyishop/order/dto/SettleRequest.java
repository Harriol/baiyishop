package com.harriol.baiyishop.order.dto;

import java.util.List;

/** 结算试算请求（docs/api.md 4.7）：购物车结算或立即购买。 */
public record SettleRequest(String source, List<Long> cartItemIds, Long skuId, Integer quantity) {
}
