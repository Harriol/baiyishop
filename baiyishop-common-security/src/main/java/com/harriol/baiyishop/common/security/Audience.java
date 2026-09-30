package com.harriol.baiyishop.common.security;

/**
 * 令牌受众。用户令牌与后台令牌使用不同 aud 与不同密钥，互不通用（docs/adr/ADR-003）。
 */
public enum Audience {

    /** 用户端（Web / 小程序） */
    USER("user"),

    /** 运营后台 */
    ADMIN("admin");

    private final String value;

    Audience(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Audience of(String value) {
        for (Audience a : values()) {
            if (a.value.equals(value)) {
                return a;
            }
        }
        throw new IllegalArgumentException("未知受众: " + value);
    }
}
