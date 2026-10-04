package com.harriol.baiyishop.product.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.product.dto.BrandResponse;
import com.harriol.baiyishop.product.service.BrandService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台品牌接口（docs/api.md 4.3、REQ-202）。
 * <p>公开接口，仅返回启用中的品牌。
 */
@RestController
@RequestMapping("/api/v1/brands")
public class BrandController {

    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @GetMapping
    public Result<List<BrandResponse>> list() {
        return Result.ok(brandService.enabledBrands());
    }
}