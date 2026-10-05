package com.harriol.baiyishop.order.dto;

import java.util.List;

/**
 * 购物车视图（docs/api.md 4.6、REQ-601 / REQ-602）。
 *
 * @param checkedAmount 勾选且有效条目的合计金额（分）；全场包邮，不含运费
 * @param checkedCount  勾选且有效条目的数量合计
 */
public record CartView(List<CartItemView> items, Long checkedAmount, Integer checkedCount) {
}
