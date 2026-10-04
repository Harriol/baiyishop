package com.harriol.baiyishop.inventory.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.inventory.dto.StockAvailable;
import com.harriol.baiyishop.inventory.dto.StockOpRequest;
import com.harriol.baiyishop.inventory.dto.StockOpResult;
import com.harriol.baiyishop.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}