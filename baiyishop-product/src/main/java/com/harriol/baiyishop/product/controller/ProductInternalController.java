package com.harriol.baiyishop.product.controller;

import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.product.dto.ProductIndexDoc;
import com.harriol.baiyishop.product.dto.ProductSkuSnapshot;
import com.harriol.baiyishop.product.service.ProductIndexService;
import com.harriol.baiyishop.product.service.ProductSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品内部接口（docs/api.md 第 6 章）。
 * <p>网关对外拦截 /internal/**（返回 404），因此这里不做令牌校验 —— 调用方是 search-service
 * 这类内部服务，身份由链路本身保证。
 * <p>商品不存在 / 已删除时返回 code 30007（HTTP 200），search-service 据此移除索引文档。
 */
@RestController
@RequestMapping("/internal/products")
public class ProductInternalController {

    private final ProductIndexService indexService;
    private final ProductSnapshotService snapshotService;

    public ProductInternalController(ProductIndexService indexService, ProductSnapshotService snapshotService) {
        this.indexService = indexService;
        this.snapshotService = snapshotService;
    }

    /** 单个商品的索引文档（增量同步用） */
    @GetMapping("/{id}/index-doc")
    public Result<ProductIndexDoc> indexDoc(@PathVariable Long id) {
        return Result.ok(indexService.indexDoc(id));
    }

    /** 索引文档分页（全量重建用，一页一页拉直到 total 拉完） */
    @GetMapping("/index-docs")
    public Result<PageResult<ProductIndexDoc>> indexDocs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "200") long size) {
        return Result.ok(indexService.indexDocPage(page, size));
    }

    // ---------------- 快照（order-service 使用） ----------------

    /** 商品快照：与索引文档同字段，下单前校验商品存在性/状态用 */
    @GetMapping("/{id}")
    public Result<ProductIndexDoc> product(@PathVariable Long id) {
        return Result.ok(indexService.indexDoc(id));
    }

    /** 单个 SKU 快照 */
    @GetMapping("/skus/{skuId}")
    public Result<ProductSkuSnapshot> sku(@PathVariable Long skuId) {
        return Result.ok(snapshotService.skuSnapshot(skuId));
    }

    /** 批量 SKU 快照（购物车列表 / 结算试算用，一次取回避免 N+1） */
    @GetMapping("/skus")
    public Result<List<ProductSkuSnapshot>> skus(@RequestParam List<Long> skuIds) {
        return Result.ok(snapshotService.skuSnapshots(skuIds));
    }
}
