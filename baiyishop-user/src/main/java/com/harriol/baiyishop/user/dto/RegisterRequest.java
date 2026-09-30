package com.harriol.baiyishop.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 注册请求（REQ-101）。 */
public record RegisterRequest(
        @NotBlank(message = "请输入账号")
        @Pattern(regexp = "^[A-Za-z0-9_]{4,20}$", message = "账号需为 4-20 位字母、数字或下划线")
        String username,

        @NotBlank(message = "请输入密码")
        @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
        String password,

        @Size(max = 50, message = "昵称最长 50 个字符")
        String nickname) {
}
