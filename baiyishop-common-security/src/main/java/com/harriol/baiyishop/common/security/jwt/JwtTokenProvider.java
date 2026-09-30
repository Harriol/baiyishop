package com.harriol.baiyishop.common.security.jwt;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.security.Audience;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * JWT 签发与校验（docs/adr/ADR-003）。
 * <p>用户令牌与后台令牌使用不同密钥与不同 aud，互不通用；校验失败统一抛 401 语义的业务异常。
 * <p>密钥缺失或长度不足时在启动阶段即失败，避免运行到第一次请求才发现配置问题。
 */
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";

    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        // 启动即校验两个密钥，配置问题早暴露
        properties.secretOf(Audience.USER);
        properties.secretOf(Audience.ADMIN);
    }

    public String createAccessToken(long userId, Audience audience, String role) {
        return create(userId, audience, role, TokenPayload.TYPE_ACCESS, properties.getAccessTtl());
    }

    public String createRefreshToken(long userId, Audience audience, String role) {
        return create(userId, audience, role, TokenPayload.TYPE_REFRESH, properties.getRefreshTtl());
    }

    private String create(long userId, Audience audience, String role, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .audience().add(audience.value()).and()
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TYPE, type)
                .id(UUID.randomUUID().toString().replace("-", ""))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(keyOf(audience))
                .compact();
    }

    /**
     * 校验并解析令牌。受众不匹配、签名错误、过期、格式非法一律按未认证处理。
     */
    public TokenPayload parse(String token, Audience audience) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(keyOf(audience))
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Set<String> audiences = claims.getAudience();
            if (audiences == null || !audiences.contains(audience.value())) {
                throw new BizException(ErrorCode.UNAUTHORIZED);
            }
            return new TokenPayload(
                    Long.parseLong(claims.getSubject()),
                    audience,
                    claims.get(CLAIM_ROLE, String.class),
                    claims.getId(),
                    claims.getExpiration().toInstant(),
                    claims.get(CLAIM_TYPE, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private SecretKey keyOf(Audience audience) {
        return Keys.hmacShaKeyFor(properties.secretOf(audience).getBytes(StandardCharsets.UTF_8));
    }
}
