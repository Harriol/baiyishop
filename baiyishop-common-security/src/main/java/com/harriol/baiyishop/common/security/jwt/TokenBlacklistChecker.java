package com.harriol.baiyishop.common.security.jwt;

/**
 * 令牌黑名单钩子。注销后的 access token 在其剩余有效期内应被拒绝。
 * <p>common-security 不绑定具体存储，由使用方提供实现（例如基于 Redis 的实现）；
 * 未提供实现时视为不启用黑名单。
 */
public interface TokenBlacklistChecker {

    /**
     * @param tokenId 令牌的 jti
     * @return true 表示该令牌已被注销
     */
    boolean isBlacklisted(String tokenId);
}
