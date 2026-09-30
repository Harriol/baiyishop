package com.harriol.baiyishop.common.security.jwt;

import com.harriol.baiyishop.common.security.Audience;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置。两个受众使用**不同密钥**，密钥不写进代码，由环境变量或本地配置文件提供（ADR-003、NFR-03）。
 */
@ConfigurationProperties(prefix = "baiyishop.jwt")
public class JwtProperties {

    /** 用户端令牌签名密钥（HS256 要求至少 32 字节） */
    private String userSecret;

    /** 后台令牌签名密钥 */
    private String adminSecret;

    /** 访问令牌有效期，默认 2 小时 */
    private Duration accessTtl = Duration.ofHours(2);

    /** 刷新令牌有效期，默认 7 天 */
    private Duration refreshTtl = Duration.ofDays(7);

    /** 签发者标识 */
    private String issuer = "baiyishop";

    String secretOf(Audience audience) {
        String secret = audience == Audience.ADMIN ? adminSecret : userSecret;
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "未配置 baiyishop.jwt." + (audience == Audience.ADMIN ? "admin-secret" : "user-secret")
                            + "，或长度不足 32 字节；请检查本地配置或环境变量");
        }
        return secret;
    }

    public String getUserSecret() {
        return userSecret;
    }

    public void setUserSecret(String userSecret) {
        this.userSecret = userSecret;
    }

    public String getAdminSecret() {
        return adminSecret;
    }

    public void setAdminSecret(String adminSecret) {
        this.adminSecret = adminSecret;
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public void setAccessTtl(Duration accessTtl) {
        this.accessTtl = accessTtl;
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    public void setRefreshTtl(Duration refreshTtl) {
        this.refreshTtl = refreshTtl;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
