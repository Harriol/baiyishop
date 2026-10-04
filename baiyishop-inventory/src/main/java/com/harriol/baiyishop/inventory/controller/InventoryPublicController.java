package com.harriol.baiyishop.inventory.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.inventory.dto.StockAvailable;
import com.harriol.baiyishop.inventory.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库存只读接口（docs/api.md 4.5），对游客公开。
 * <p>正常链路上商品详情 / 列表的库存展示由 product-service 聚合，这组接口供前端需要刷新库存时使用。
 * <p>没有库存记录时 available 返回 null —— 前端要能区分「未配置库存」与「库存为 0」。
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryPublicController {

    private final InventoryService inventoryService;

    public InventoryPublicController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** 单个 SKU 可售数量 */
    @GetMapping("/skus/{skuId}/available")
    public Result<StockAvailable> available(@PathVariable Long skuId) {
        return Result.ok(inventoryService.available(skuId));
    }

    /** 批量可售数量 */
    @GetMapping("/skus/available")
    public Result<List<StockAvailable>> availableBatch(@RequestParam List<Long> skuIds) {
        return Result.ok(inventoryService.availableBatch(skuIds));
    }
}