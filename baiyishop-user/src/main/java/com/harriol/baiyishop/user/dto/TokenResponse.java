package com.harriol.baiyishop.user.dto;

/**
 * 登录 / 注册 / 刷新成功后返回的令牌与用户信息（docs/api.md 4.1）。
 *
 * @param expiresIn accessToken 有效期（秒）
 */
public record TokenResponse(String accessToken, String refreshToken, long expiresIn, UserProfileResponse user) {
}
