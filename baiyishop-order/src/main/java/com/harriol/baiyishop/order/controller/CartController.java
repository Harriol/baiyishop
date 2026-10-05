package com.harriol.baiyishop.order.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.context.UserContext;
import com.harriol.baiyishop.order.dto.CartCheckedRequest;
import com.harriol.baiyishop.order.dto.CartItemAddRequest;
import com.harriol.baiyishop.order.dto.CartItemUpdateRequest;
import com.harriol.baiyishop.order.dto.CartView;
import com.harriol.baiyishop.order.service.CartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 购物车接口（docs/api.md 4.6、REQ-601）。
 * <p>全部需要登录；用户身份取自令牌上下文，路径与请求体里都不带 userId（NFR-03）。
 * <p>写操作返回**整份购物车**：前端不必再补一次查询，合计金额也由服务端算好。
 */
@RestController
@RequestMapping("/api/v1/carts")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /** 购物车列表（含试算合计与失效标记） */
    @GetMapping
    public Result<CartView> list() {
        return Result.ok(cartService.list(UserContext.requireUserId()));
    }

    /** 加入购物车（同 SKU 数量累加） */
    @PostMapping("/items")
    public Result<CartView> add(@Valid @RequestBody CartItemAddRequest request) {
        return Result.ok(cartService.add(UserContext.requireUserId(), request.skuId(), request.quantity()));
    }

    /** 修改数量 / 勾选状态 */
    @PutMapping("/items/{id}")
    public Result<CartView> update(@PathVariable long id, @Valid @RequestBody CartItemUpdateRequest request) {
        return Result.ok(cartService.update(UserContext.requireUserId(), id, request.quantity(), request.checked()));
    }

    /** 删除条目 */
    @DeleteMapping("/items/{id}")
    public Result<CartView> delete(@PathVariable long id) {
        return Result.ok(cartService.delete(UserContext.requireUserId(), id));
    }

    /** 全选 / 取消全选 */
    @PutMapping("/checked")
    public Result<CartView> checkAll(@Valid @RequestBody CartCheckedRequest request) {
        return Result.ok(cartService.checkAll(UserContext.requireUserId(), request.checked()));
    }
}
