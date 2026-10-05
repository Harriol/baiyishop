package com.harriol.baiyishop.seckill.dto;

/** 秒杀订单取消事件（order → seckill）：把库存回补秒杀池并恢复限购计数（REQ-905）。 */
public record OrderCancelEvent(String orderNo) {
}
