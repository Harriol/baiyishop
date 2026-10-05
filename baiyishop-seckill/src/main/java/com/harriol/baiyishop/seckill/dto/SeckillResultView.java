package com.harriol.baiyishop.seckill.dto;

/**
 * 抢购结果（REQ-904）。
 *
 * @param message 失败原因的中文提示，前端可直接展示
 */
public record SeckillResultView(String ticketId, String status, String orderNo,
                                String failReason, String message) {
}
