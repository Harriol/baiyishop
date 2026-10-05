package com.harriol.baiyishop.seckill.dto;

/** 抢购受理结果（REQ-903、REQ-904）：立即返回，前端凭 ticketId 轮询。 */
public record SeckillTicketView(String ticketId, String status) {
}
