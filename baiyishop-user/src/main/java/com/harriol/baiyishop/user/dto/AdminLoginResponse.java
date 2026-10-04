package com.harriol.baiyishop.user.dto;

/**
 * 后台登录响应。后台与前台令牌使用不同密钥与不同受众，互不通用（docs/adr/ADR-003）。
 *
 * @param expiresIn accessToken 有效期（秒）
 */
public record AdminLoginResponse(String accessToken, long expiresIn, AdminProfileResponse admin) {
}