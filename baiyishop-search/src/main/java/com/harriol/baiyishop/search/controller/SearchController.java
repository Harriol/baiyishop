package com.harriol.baiyishop.search.controller;

import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.search.dto.SearchItem;
import com.harriol.baiyishop.search.index.ProductSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品搜索（docs/api.md 4.4、REQ-301），对游客公开。
 * <p>price 相关参数与返回值都以「分」为单位，与 product-service 的商品列表保持一致
 * （前端统一做展示换算）。无结果时返回空数组与 total=0，由前端展示空态。
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final ProductSearchService searchService;

    public SearchController(ProductSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/products")
    public Result<PageResult<SearchItem>> products(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(searchService.search(keyword, categoryId, brandId, minPrice, maxPrice, sort, page, size));
    }
}
