package com.harriol.baiyishop.product.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.annotation.RequiresRole;
import com.harriol.baiyishop.product.dto.CategoryRequest;
import com.harriol.baiyishop.product.dto.CategoryResponse;
import com.harriol.baiyishop.product.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台分类管理（docs/api.md 5.2、REQ-201）。
 * <p>超管与运营可维护；客服访问返回 403。
 */
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiresRole({"SUPER_ADMIN", "OPERATOR"})
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** 分类树（含隐藏） */
    @GetMapping
    public Result<List<CategoryResponse>> tree() {
        return Result.ok(categoryService.tree(true));
    }

    /** 新增分类 */
    @PostMapping
    public Result<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return Result.ok(categoryService.create(request));
    }

    /** 修改分类（含调整父级，物化路径自动重算） */
    @PutMapping("/{id}")
    public Result<CategoryResponse> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return Result.ok(categoryService.update(id, request));
    }

    /** 删除分类：有子分类或被商品引用时拒绝 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }

    /** 显示 / 隐藏 */
    @PutMapping("/{id}/visible")
    public Result<CategoryResponse> setVisible(@PathVariable Long id, @RequestParam boolean visible) {
        return Result.ok(categoryService.setVisible(id, visible));
    }
}