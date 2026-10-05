package com.harriol.baiyishop.order.dto;

/**
 * 待付款超时事件（REQ-704）。
 * <p>消费端只按 orderNo 回查订单状态并做条件更新，因此消息重复投递无副作用；
 * 即使消息提前到达（定时消息降级为立即投递），消费端也会因「尚未超时」而跳过。
 */
public record OrderTimeoutEvent(String orderNo) {
}
