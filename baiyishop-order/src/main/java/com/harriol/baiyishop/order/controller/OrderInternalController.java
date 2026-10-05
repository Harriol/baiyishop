package com.harriol.baiyishop.order.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.order.dto.PayableOrderView;
import com.harriol.baiyishop.order.dto.TicketOrderView;
import com.harriol.baiyishop.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单域内部接口（docs/api.md 第 6 章）。
 * <p>网关对外拦截 /internal/**（返回 404），这里不做令牌校验；订单不存在返回 50001。
 */
@RestController
@RequestMapping("/internal/orders")
public class OrderInternalController {

    private final OrderService orderService;

    public OrderInternalController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 校验订单可支付与金额一致（REQ-801）：payment-service 发起支付前回查 */
    @GetMapping("/{orderNo}/payable")
    public Result<PayableOrderView> payable(@PathVariable String orderNo) {
        return Result.ok(orderService.payableView(orderNo));
    }

    /** 按秒杀票据回查订单（seckill 对账用，REQ-903）；没有对应订单返回 50001 */
    @GetMapping("/by-ticket/{ticketId}")
    public Result<TicketOrderView> byTicket(@PathVariable String ticketId) {
        return Result.ok(orderService.ticketView(ticketId));
    }
}
