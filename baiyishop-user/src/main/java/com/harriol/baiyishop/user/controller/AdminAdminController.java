package com.harriol.baiyishop.user.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.annotation.RequiresRole;
import com.harriol.baiyishop.user.dto.AdminProfileResponse;
import com.harriol.baiyishop.user.service.AdminAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员与角色管理（docs/api.md 5.1、REQ-106）。
 * <p>整个模块仅超级管理员可访问；运营与客服访问返回 403。
 */
@RestController
@RequestMapping("/api/v1/admin/admins")
@RequiresRole({"SUPER_ADMIN"})
public class AdminAdminController {

    private final AdminAuthService adminAuthService;

    public AdminAdminController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    /** 管理员列表 */
    @GetMapping
    public Result<List<AdminProfileResponse>> list() {
        return Result.ok(adminAuthService.listAdmins());
    }
}