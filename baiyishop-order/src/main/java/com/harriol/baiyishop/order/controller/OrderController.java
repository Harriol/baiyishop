package com.harriol.baiyishop.order.controller;

import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.context.UserContext;
import com.harriol.baiyishop.order.dto.CreateOrderRequest;
import com.harriol.baiyishop.order.dto.OrderCreateResponse;
import com.harriol.baiyishop.order.dto.OrderDetailView;
import com.harriol.baiyishop.order.dto.OrderSummary;
import com.harriol.baiyishop.order.dto.SettleRequest;
import com.harriol.baiyishop.order.dto.SettleView;
import com.harriol.baiyishop.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户侧订单接口（docs/api.md 4.7、REQ-701 ~ REQ-706）。
 * <p>全部需要登录，用户身份取自令牌上下文（路径与请求体都不带 userId）。
 * <p>下单要求 {@code X-Request-Id}：它是请求级幂等键，重复提交返回首次订单号（REQ-701 约定 2）。
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 结算试算：购物车勾选项或立即购买，只读不落库（REQ-602） */
    @PostMapping("/settle")
    public Result<SettleView> settle(@RequestBody SettleRequest request) {
        return Result.ok(orderService.settle(UserContext.requireUserId(), request));
    }

    /** 提交订单 */
    @PostMapping
    public Result<OrderCreateResponse> create(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                              @RequestBody CreateOrderRequest request) {
        return Result.ok(orderService.create(UserContext.requireUserId(), requestId, request));
    }

    /** 我的订单列表（按状态筛选 + 分页） */
    @GetMapping
    public Result<PageResult<OrderSummary>> page(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(orderService.page(UserContext.requireUserId(), status, page, size));
    }

    /** 订单详情（含明细快照与状态日志） */
    @GetMapping("/{orderNo}")
    public Result<OrderDetailView> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.detail(UserContext.requireUserId(), orderNo));
    }

    /** 取消订单（仅待付款） */
    @PutMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable String orderNo) {
        orderService.cancel(UserContext.requireUserId(), orderNo);
        return Result.ok();
    }

    /** 确认收货（仅待收货） */
    @PutMapping("/{orderNo}/receive")
    public Result<Void> receive(@PathVariable String orderNo) {
        orderService.receive(UserContext.requireUserId(), orderNo);
        return Result.ok();
    }
}
