package com.harriol.baiyishop.order.dto;

/** 秒杀订单取消事件（order → seckill，REQ-905）：秒杀侧据此回补秒杀池与限购计数。 */
public record OrderCancelEvent(String orderNo) {
}
