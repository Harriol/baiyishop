package com.harriol.baiyishop.user.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.context.UserContext;
import com.harriol.baiyishop.user.dto.UpdateProfileRequest;
import com.harriol.baiyishop.user.dto.UserProfileResponse;
import com.harriol.baiyishop.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户资料（docs/api.md 4.1、REQ-104）。
 * <p>身份一律取自令牌上下文，**不接受前端传入 userId**（NFR-03）。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public Result<UserProfileResponse> me() {
        return Result.ok(authService.profile(UserContext.requireUserId()));
    }

    /** 修改当前用户资料：昵称 / 头像 / 手机号（REQ-104） */
    @PutMapping("/me")
    public Result<UserProfileResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.ok(authService.updateProfile(UserContext.requireUserId(), request));
    }
}
