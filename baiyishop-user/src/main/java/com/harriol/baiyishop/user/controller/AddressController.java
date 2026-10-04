package com.harriol.baiyishop.user.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.context.UserContext;
import com.harriol.baiyishop.user.dto.AddressRequest;
import com.harriol.baiyishop.user.dto.AddressResponse;
import com.harriol.baiyishop.user.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址接口（docs/api.md 4.2、REQ-103）。
 * <p>全部需要登录；用户身份取自令牌上下文，路径里不带 userId。
 */
@RestController
@RequestMapping("/api/v1/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    /** 地址列表（默认地址排首位） */
    @GetMapping
    public Result<List<AddressResponse>> list() {
        return Result.ok(addressService.list(UserContext.requireUserId()));
    }

    /** 新增地址 */
    @PostMapping
    public Result<AddressResponse> create(@Valid @RequestBody AddressRequest request) {
        return Result.ok(addressService.create(UserContext.requireUserId(), request));
    }

    /** 修改地址 */
    @PutMapping("/{id}")
    public Result<AddressResponse> update(@PathVariable long id, @Valid @RequestBody AddressRequest request) {
        return Result.ok(addressService.update(UserContext.requireUserId(), id, request));
    }

    /** 删除地址 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        addressService.delete(UserContext.requireUserId(), id);
        return Result.ok();
    }

    /** 设为默认地址 */
    @PutMapping("/{id}/default")
    public Result<AddressResponse> setDefault(@PathVariable long id) {
        return Result.ok(addressService.setDefault(UserContext.requireUserId(), id));
    }
}