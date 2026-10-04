package com.harriol.baiyishop.user.dto;

import jakarta.validation.constraints.NotBlank;

/** 后台管理员登录请求（REQ-105）。 */
public record AdminLoginRequest(
        @NotBlank(message = "请输入账号") String username,
        @NotBlank(message = "请输入密码") String password) {
}