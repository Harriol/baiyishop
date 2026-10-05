package com.harriol.baiyishop.inventory.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.inventory.dto.SeckillAllocateRequest;
import com.harriol.baiyishop.inventory.dto.SeckillPoolItem;
import com.harriol.baiyishop.inventory.dto.SeckillReturnRequest;
import com.harriol.baiyishop.inventory.dto.StockAvailable;
import com.harriol.baiyishop.inventory.dto.StockOpRequest;
import com.harriol.baiyishop.inventory.dto.StockOpResult;
import com.harriol.baiyishop.inventory.service.InventoryService;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库存内部接口（docs/api.md 第 6 章），仅供服务间调用。
 * <p>网关对外部请求一律返回 404（架构 4.3），因此这里不做令牌校验 —— 调用方是
 * order-service 这类内部服务，身份由链路本身保证。
 * <p>三个写接口都带 orderNo，它同时是幂等键：同一订单重复调用只生效一次（REQ-503）。
 */
@RestController
@RequestMapping("/internal/inventory")
public class InventoryInternalController {

    private final InventoryService inventoryService;

    public InventoryInternalController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** 下单预占库存（REQ-502） */
    @PostMapping("/lock")
    public Result<StockOpResult> lock(@Valid @RequestBody StockOpRequest request) {
        return Result.ok(inventoryService.lock(request.orderNo(), request.productId(), request.items()));
    }

    /** 支付成功扣减库存（REQ-503） */
    @PostMapping("/deduct")
    public Result<StockOpResult> deduct(@Valid @RequestBody StockOpRequest request) {
        return Result.ok(inventoryService.deduct(request.orderNo(), request.productId(), request.items()));
    }

    /** 取消 / 超时释放库存（REQ-503） */
    @PostMapping("/release")
    public Result<StockOpResult> release(@Valid @RequestBody StockOpRequest request) {
        return Result.ok(inventoryService.release(request.orderNo(), request.productId(), request.items()));
    }

    /** 只读库存，供 product-service 聚合商品详情（架构 4.3） */
    @GetMapping("/skus/{skuId}")
    public Result<StockAvailable> available(@PathVariable Long skuId) {
        return Result.ok(inventoryService.available(skuId));
    }

    /** 批量只读库存，供 order-service 渲染购物车（一次取回，避免 N+1） */
    @GetMapping("/skus/available")
    public Result<List<StockAvailable>> availableBatch(@RequestParam List<Long> skuIds) {
        return Result.ok(inventoryService.availableBatch(skuIds));
    }

    // ---------------- 秒杀库存池（REQ-504、REQ-905） ----------------

    /** 划拨普通库存到秒杀池：划拨即扣减普通库存 */
    @PostMapping("/seckill/allocate")
    public Result<SeckillPoolItem> allocate(@Valid @RequestBody SeckillAllocateRequest request) {
        return Result.ok(inventoryService.allocate(request));
    }

    /** 回补：UNSOLD 回补普通库存 / ROLLBACK 回滚到秒杀池 */
    @PostMapping("/seckill/return")
    public Result<SeckillPoolItem> returnStock(@Valid @RequestBody SeckillReturnRequest request) {
        return Result.ok(inventoryService.returnStock(request));
    }

    /** 秒杀成交扣减秒杀池 */
    @PostMapping("/seckill/deduct")
    public Result<SeckillPoolItem> deductSeckill(@RequestParam Long activitySkuId,
                                                 @RequestParam @Min(1) int quantity,
                                                 @RequestParam String orderNo) {
        return Result.ok(inventoryService.deductSeckill(activitySkuId, quantity, orderNo));
    }

    /** 查询秒杀池现状（活动展示与对账用） */
    @GetMapping("/seckill/pool/{activitySkuId}")
    public Result<SeckillPoolItem> pool(@PathVariable Long activitySkuId) {
        return Result.ok(inventoryService.poolOf(activitySkuId));
    }
}
