package com.harriol.baiyishop.product.controller;

import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.product.dto.CategoryResponse;
import com.harriol.baiyishop.product.dto.ProductListItem;
import com.harriol.baiyishop.product.service.CategoryService;
import com.harriol.baiyishop.product.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台分类接口（docs/api.md 4.3、REQ-205）。
 * <p>公开接口：分类树只返回可见分类；分类商品列表**含子分类**且只返回上架商品，
 * 分类下没有商品时返回空列表，由前端展示空态。
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final ProductService productService;

    public CategoryController(CategoryService categoryService, ProductService productService) {
        this.categoryService = categoryService;
        this.productService = productService;
    }

    /** 三级分类树（仅可见） */
    @GetMapping("/tree")
    public Result<List<CategoryResponse>> tree() {
        return Result.ok(categoryService.tree(false));
    }

    /** 分类商品列表；sort 取 sales / new / price_asc / price_desc */
    @GetMapping("/{id}/products")
    public Result<PageResult<ProductListItem>> products(
            @PathVariable Long id,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(productService.publicPage(id, sort, page, size));
    }
}