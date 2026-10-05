package com.harriol.baiyishop.user.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.user.dto.AddressSnapshot;
import com.harriol.baiyishop.user.dto.AdminNameView;
import com.harriol.baiyishop.user.service.InternalSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户域内部接口（docs/api.md 第 6 章）。
 * <p>网关对外拦截 /internal/**（返回 404），这里不做令牌校验。
 */
@RestController
@RequestMapping("/internal/users")
public class UserInternalController {

    private final InternalSnapshotService snapshotService;

    public UserInternalController(InternalSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    /** 收货地址快照（下单时读取，事务外；含完整手机号） */
    @GetMapping("/addresses/{id}")
    public Result<AddressSnapshot> address(@PathVariable long id) {
        return Result.ok(snapshotService.address(id));
    }

    /** 结算页默认地址（没有地址时 data 为 null，由前端引导新建） */
    @GetMapping("/addresses/default/{userId}")
    public Result<AddressSnapshot> defaultAddress(@PathVariable long userId) {
        return Result.ok(snapshotService.defaultAddress(userId));
    }

    /** 管理员姓名（后台备注留存姓名快照用） */
    @GetMapping("/admins/{id}")
    public Result<AdminNameView> admin(@PathVariable long id) {
        return Result.ok(snapshotService.admin(id));
    }
}
