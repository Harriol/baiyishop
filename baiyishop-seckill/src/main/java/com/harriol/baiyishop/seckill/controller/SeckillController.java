package com.harriol.baiyishop.seckill.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.context.UserContext;
import com.harriol.baiyishop.seckill.dto.ActivityView;
import com.harriol.baiyishop.seckill.dto.SeckillBuyRequest;
import com.harriol.baiyishop.seckill.dto.SeckillResultView;
import com.harriol.baiyishop.seckill.dto.SeckillTicketView;
import com.harriol.baiyishop.seckill.service.SeckillActivityService;
import com.harriol.baiyishop.seckill.service.SeckillBuyService;
import com.harriol.baiyishop.seckill.service.SeckillResultService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台秒杀接口（docs/api.md 4.9、REQ-902 ~ REQ-904）。
 * <p>专区与详情公开；抢购与结果查询需要登录（抢购还要 `X-Request-Id`）。
 */
@RestController
@RequestMapping("/api/v1/seckill")
public class SeckillController {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_NOT_STARTED = "NOT_STARTED";

    private final SeckillActivityService activityService;
    private final SeckillBuyService buyService;
    private final SeckillResultService resultService;

    public SeckillController(SeckillActivityService activityService,
                             SeckillBuyService buyService,
                             SeckillResultService resultService) {
        this.activityService = activityService;
        this.buyService = buyService;
        this.resultService = resultService;
    }

    /** 秒杀专区：进行中 + 即将开始（REQ-902） */
    @GetMapping("/activities")
    public Result<List<ActivityView>> activities(@RequestParam(defaultValue = "10") long limit) {
        long size = Math.min(Math.max(limit, 1), 20);
        List<ActivityView> running = activityService.page(1, size, STATUS_RUNNING).getList();
        List<ActivityView> upcoming = activityService.page(1, size, STATUS_NOT_STARTED).getList();
        return Result.ok(java.util.stream.Stream.concat(running.stream(), upcoming.stream()).toList());
    }

    /** 场次详情（含商品、秒杀价、剩余量与倒计时，REQ-902） */
    @GetMapping("/activities/{id}")
    public Result<ActivityView> detail(@PathVariable long id) {
        return Result.ok(activityService.detail(id));
    }

    /** 抢购（REQ-903）：立即返回排队票据，前端凭 ticketId 轮询结果 */
    @PostMapping("/activities/skus/{activitySkuId}/orders")
    public Result<SeckillTicketView> buy(@PathVariable long activitySkuId,
                                         @Valid @RequestBody SeckillBuyRequest request) {
        return Result.ok(buyService.buy(UserContext.requireUserId(), activitySkuId, request));
    }

    /** 轮询抢购结果（REQ-904） */
    @GetMapping("/results/{ticketId}")
    public Result<SeckillResultView> result(@PathVariable String ticketId) {
        return Result.ok(resultService.result(UserContext.requireUserId(), ticketId));
    }
}
