package com.harriol.baiyishop.common.security.context;

import com.harriol.baiyishop.common.security.Audience;

/**
 * 当前请求的身份。
 *
 * @param userId   用户 / 管理员 ID
 * @param audience 受众
 * @param role     角色码（后台 RBAC 用）
 */
public record UserPrincipal(long userId, Audience audience, String role) {

    public boolean isAdmin() {
        return audience == Audience.ADMIN;
    }
}
