package com.harriol.baiyishop.user.cache;

import com.harriol.baiyishop.common.security.jwt.TokenBlacklistChecker;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis 的令牌黑名单（注销后 access token 立即失效）。
 * <p>key 规范见 docs/architecture.md 7.1：baiyishop:user:token:blacklist:{jti}
 */
@Component
public class RedisTokenBlacklistChecker implements TokenBlacklistChecker {

    public static final String BLACKLIST_KEY_PREFIX = "baiyishop:user:token:blacklist:";

    private final StringRedisTemplate redis;

    public RedisTokenBlacklistChecker(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean isBlacklisted(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_KEY_PREFIX + tokenId));
    }
}
