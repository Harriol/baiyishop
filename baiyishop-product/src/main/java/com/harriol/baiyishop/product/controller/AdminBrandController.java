package com.harriol.baiyishop.product.controller;

import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.annotation.RequiresRole;
import com.harriol.baiyishop.product.dto.BrandRequest;
import com.harriol.baiyishop.product.dto.BrandResponse;
import com.harriol.baiyishop.product.service.BrandService;
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

/**
 * 后台品牌管理（docs/api.md 5.2、REQ-202）。
 * <p>超管与运营可维护；客服访问返回 403。
 */
@RestController
@RequestMapping("/api/v1/admin/brands")
@RequiresRole({"SUPER_ADMIN", "OPERATOR"})
public class AdminBrandController {

    private final BrandService brandService;

    public AdminBrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    /** 品牌分页列表 */
    @GetMapping
    public Result<PageResult<BrandResponse>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled) {
        return Result.ok(brandService.page(page, size, keyword, enabled));
    }

    /** 新增品牌 */
    @PostMapping
    public Result<BrandResponse> create(@Valid @RequestBody BrandRequest request) {
        return Result.ok(brandService.create(request));
    }

    /** 修改品牌 */
    @PutMapping("/{id}")
    public Result<BrandResponse> update(@PathVariable Long id, @Valid @RequestBody BrandRequest request) {
        return Result.ok(brandService.update(id, request));
    }

    /** 删除品牌：被商品引用时拒绝 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        brandService.delete(id);
        return Result.ok();
    }

    /** 启用 / 停用 */
    @PutMapping("/{id}/enabled")
    public Result<BrandResponse> setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return Result.ok(brandService.setEnabled(id, enabled));
    }
}