package com.harriol.baiyishop.common.security.jwt;

import com.harriol.baiyishop.common.security.Audience;

import java.time.Instant;

/**
 * 令牌解析结果。
 *
 * @param userId    用户 / 管理员 ID
 * @param audience  受众（user / admin）
 * @param role      角色码（后台用于 RBAC，用户端一般为空）
 * @param tokenId   jti，注销时写入黑名单
 * @param expiresAt 过期时间
 * @param type      access / refresh
 */
public record TokenPayload(long userId, Audience audience, String role, String tokenId, Instant expiresAt, String type) {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    public boolean isAccessToken() {
        return TYPE_ACCESS.equals(type);
    }

    public boolean isRefreshToken() {
        return TYPE_REFRESH.equals(type);
    }
}
