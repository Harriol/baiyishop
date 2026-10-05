package com.harriol.baiyishop.order.dto;

/** 发货后自动确认收货事件（REQ-707）。 */
public record OrderAutoReceiveEvent(String orderNo) {
}
