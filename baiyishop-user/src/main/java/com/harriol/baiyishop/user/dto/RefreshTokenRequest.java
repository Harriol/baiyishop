package com.harriol.baiyishop.user.dto;

import jakarta.validation.constraints.NotBlank;

/** 刷新令牌请求（docs/api.md 4.1）。 */
public record RefreshTokenRequest(@NotBlank(message = "缺少 refreshToken") String refreshToken) {
}
