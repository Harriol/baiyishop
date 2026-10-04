package com.harriol.baiyishop.user.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.annotation.RequiresRole;
import com.harriol.baiyishop.common.security.context.UserContext;
import com.harriol.baiyishop.user.dto.AdminLoginRequest;
import com.harriol.baiyishop.user.dto.AdminLoginResponse;
import com.harriol.baiyishop.user.dto.AdminProfileResponse;
import com.harriol.baiyishop.user.service.AdminAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台认证接口（docs/api.md 5.1）。
 * <p>登录是公开路径（网关白名单放行）；其余接口要求后台令牌。
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    /** 管理员登录（REQ-105） */
    @PostMapping("/login")
    public Result<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return Result.ok(adminAuthService.login(request));
    }

    /** 当前管理员信息与权限码列表，供后台渲染菜单（三种后台角色都可访问） */
    @GetMapping("/me")
    @RequiresRole({"SUPER_ADMIN", "OPERATOR", "SERVICE"})
    public Result<AdminProfileResponse> me() {
        return Result.ok(adminAuthService.profile(UserContext.requireAdminId()));
    }
}