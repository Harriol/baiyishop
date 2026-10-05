package com.harriol.baiyishop.order.dto;

import java.util.List;

/** 后台订单详情：用户侧详情 + 备注（REQ-708）。 */
public record AdminOrderDetailView(OrderDetailView order, Long userId, List<OrderNoteView> notes) {
}
