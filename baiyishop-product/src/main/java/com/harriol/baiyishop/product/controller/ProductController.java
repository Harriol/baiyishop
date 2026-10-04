package com.harriol.baiyishop.product.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.product.dto.ProductPublicDetail;
import com.harriol.baiyishop.product.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台商品接口（docs/api.md 4.3、REQ-206）。
 * <p>公开接口。已下架商品返回 200 + status=OFF_SALE，由前端给提示而不是报错。
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 商品详情（主图集、SKU、价格、富文本详情；参数与库存待后续功能接入） */
    @GetMapping("/{id}")
    public Result<ProductPublicDetail> detail(@PathVariable Long id) {
        return Result.ok(productService.publicDetail(id));
    }
}