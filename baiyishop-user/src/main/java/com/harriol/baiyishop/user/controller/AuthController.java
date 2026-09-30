package com.harriol.baiyishop.user.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.SecurityConstants;
import com.harriol.baiyishop.user.dto.LoginRequest;
import com.harriol.baiyishop.user.dto.RefreshTokenRequest;
import com.harriol.baiyishop.user.dto.RegisterRequest;
import com.harriol.baiyishop.user.dto.TokenResponse;
import com.harriol.baiyishop.user.dto.WechatLoginRequest;
import com.harriol.baiyishop.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（docs/api.md 4.1）。
 * <p>路径前缀 /api/v1/auth 在网关为公开路径，不需要令牌。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 账号密码注册（REQ-101） */
    @PostMapping("/register")
    public Result<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    /** 账号密码登录（REQ-101） */
    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /** 微信授权登录（REQ-102） */
    @PostMapping("/wechat-login")
    public Result<TokenResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        return Result.ok(authService.wechatLogin(request));
    }

    /** 用 refreshToken 换取新令牌（docs/api.md 4.1） */
    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.ok(authService.refresh(request.refreshToken()));
    }

    /** 注销：access token 进黑名单（REQ-101） */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authorization != null && authorization.startsWith(SecurityConstants.BEARER_PREFIX)) {
            authService.logout(authorization.substring(SecurityConstants.BEARER_PREFIX.length()).trim());
        }
        return Result.ok();
    }
}
