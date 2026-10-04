package com.harriol.baiyishop.product.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.product.dto.CategoryResponse;
import com.harriol.baiyishop.product.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台分类接口（docs/api.md 4.3、REQ-205）。
 * <p>公开接口，只返回可见分类；空分类不下发，由前端展示空态。
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** 三级分类树（仅可见） */
    @GetMapping("/tree")
    public Result<List<CategoryResponse>> tree() {
        return Result.ok(categoryService.tree(false));
    }
}