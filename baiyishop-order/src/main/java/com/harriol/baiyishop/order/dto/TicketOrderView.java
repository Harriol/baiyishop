package com.harriol.baiyishop.order.dto;

/** 按秒杀票据回查订单（内部接口，供 seckill 对账用）。 */
public record TicketOrderView(String orderNo, String status) {
}
