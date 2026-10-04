package com.harriol.baiyishop.product.controller;

import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.product.dto.ProductIndexDoc;
import com.harriol.baiyishop.product.service.ProductIndexService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    public ProductInternalController(ProductIndexService indexService) {
        this.indexService = indexService;
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
}
