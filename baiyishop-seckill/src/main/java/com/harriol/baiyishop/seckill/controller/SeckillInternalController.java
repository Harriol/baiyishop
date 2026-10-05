package com.harriol.baiyishop.seckill.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.seckill.dto.OrderCancelEvent;
import com.harriol.baiyishop.seckill.dto.SeckillResultRequest;
import com.harriol.baiyishop.seckill.service.SeckillResultService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 秒杀内部接口（docs/api.md 第 6 章）。
 * <p>网关对外拦截 /internal/**（返回 404），这里不做令牌校验。
 */
@RestController
@RequestMapping("/internal/seckill")
public class SeckillInternalController {

    private final SeckillResultService resultService;

    public SeckillInternalController(SeckillResultService resultService) {
        this.resultService = resultService;
    }

    /** order-service 回写下单结果（成功带 orderNo；失败带原因，秒杀侧据此回补预扣） */
    @PostMapping("/records/{ticketId}/result")
    public Result<Void> applyResult(@PathVariable String ticketId,
                                    @RequestBody SeckillResultRequest request) {
        resultService.applyResult(ticketId, request);
        return Result.ok();
    }

    /** 秒杀订单取消 / 超时：回补秒杀池与限购计数（REQ-905） */
    @PostMapping("/orders/{orderNo}/cancelled")
    public Result<Void> orderCancelled(@PathVariable String orderNo) {
        resultService.onOrderCancelled(orderNo);
        return Result.ok();
    }
}
