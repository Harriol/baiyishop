package com.harriol.baiyishop.inventory.controller;

import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.annotation.RequiresRole;
import com.harriol.baiyishop.common.security.context.UserContext;
import com.harriol.baiyishop.inventory.dto.InventoryAdjustRequest;
import com.harriol.baiyishop.inventory.dto.InventoryFlowItem;
import com.harriol.baiyishop.inventory.dto.InventoryItem;
import com.harriol.baiyishop.inventory.dto.StockAlertItem;
import com.harriol.baiyishop.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台库存管理（docs/api.md 5.3、REQ-501、REQ-505）。
 * <p>超管与运营可维护；客服访问返回 403。
 */
@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiresRole({"SUPER_ADMIN", "OPERATOR"})
public class AdminInventoryController {

    private final InventoryService inventoryService;

    public AdminInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** 库存列表，可按 SKU / 商品筛选，或只看预警中的 */
    @GetMapping
    public Result<PageResult<InventoryItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Boolean onlyAlert) {
        return Result.ok(inventoryService.page(page, size, skuId, productId, onlyAlert));
    }

    /** 调整库存：正数补货、负数减库；每次调整写一条流水（REQ-501） */
    @PutMapping("/{skuId}/adjust")
    public Result<InventoryItem> adjust(@PathVariable Long skuId,
                                        @RequestParam(required = false) Long productId,
                                        @Valid @RequestBody InventoryAdjustRequest request) {
        return Result.ok(inventoryService.adjust(skuId, productId, request.delta(), request.reason(),
                UserContext.requireAdminId()));
    }

    /** 库存流水 */
    @GetMapping("/flows")
    public Result<PageResult<InventoryFlowItem>> flows(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) String type) {
        return Result.ok(inventoryService.flows(page, size, skuId, type));
    }

    /** 库存预警列表（REQ-505） */
    @GetMapping("/alerts")
    public Result<List<StockAlertItem>> alerts(@RequestParam(required = false) String status) {
        return Result.ok(inventoryService.alerts(status));
    }

    /** 关闭预警 */
    @PutMapping("/alerts/{id}/close")
    public Result<Void> closeAlert(@PathVariable Long id) {
        inventoryService.closeAlert(id);
        return Result.ok();
    }
}