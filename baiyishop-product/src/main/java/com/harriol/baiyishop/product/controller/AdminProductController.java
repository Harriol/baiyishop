package com.harriol.baiyishop.product.controller;

import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.annotation.RequiresRole;
import com.harriol.baiyishop.product.dto.ProductAdminItem;
import com.harriol.baiyishop.product.dto.ProductDetailResponse;
import com.harriol.baiyishop.product.dto.ProductRequest;
import com.harriol.baiyishop.product.dto.ProductSkuSnapshot;
import com.harriol.baiyishop.product.service.ProductService;
import com.harriol.baiyishop.product.service.ProductSnapshotService;
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
 * 后台商品管理（docs/api.md 5.2、REQ-203）。
 * <p>超管与运营可维护；客服访问返回 403。
 * <p>下架与删除分离：下架改状态，删除走逻辑删除，历史订单仍可展示商品快照。
 */
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiresRole({"SUPER_ADMIN", "OPERATOR"})
public class AdminProductController {

    private final ProductService productService;
    private final ProductSnapshotService snapshotService;

    public AdminProductController(ProductService productService, ProductSnapshotService snapshotService) {
        this.productService = productService;
        this.snapshotService = snapshotService;
    }

    /** 商品分页列表 */
    @GetMapping
    public Result<PageResult<ProductAdminItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String status) {
        return Result.ok(productService.page(page, size, keyword, categoryId, brandId, status));
    }

    /** SKU 快照：库存管理页用 SKU 反查商品，兼容历史 product_id=0 的库存记录 */
    @GetMapping("/skus/{skuId}")
    public Result<ProductSkuSnapshot> sku(@PathVariable Long skuId) {
        return Result.ok(snapshotService.skuSnapshot(skuId));
    }

    /** 商品详情（含 SKU 与图集） */
    @GetMapping("/{id}")
    public Result<ProductDetailResponse> detail(@PathVariable Long id) {
        return Result.ok(productService.detail(id));
    }

    /** 新增商品；SKU 编码由服务端生成 */
    @PostMapping
    public Result<ProductDetailResponse> create(@Valid @RequestBody ProductRequest request) {
        return Result.ok(productService.create(request));
    }

    /** 修改商品 */
    @PutMapping("/{id}")
    public Result<ProductDetailResponse> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return Result.ok(productService.update(id, request));
    }

    /** 上架 */
    @PutMapping("/{id}/on-sale")
    public Result<ProductDetailResponse> onSale(@PathVariable Long id) {
        return Result.ok(productService.changeStatus(id, true));
    }

    /** 下架 */
    @PutMapping("/{id}/off-sale")
    public Result<ProductDetailResponse> offSale(@PathVariable Long id) {
        return Result.ok(productService.changeStatus(id, false));
    }

    /** 删除商品（逻辑删除） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.ok();
    }
}
