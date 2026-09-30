package com.harriol.baiyishop.common.security;

/**
 * 鉴权相关常量。请求头由网关校验通过后注入，服务端仍会自行校验令牌（双重校验，ADR-003）。
 */
public final class SecurityConstants {

    /** 标准认证请求头 */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /** Bearer 前缀 */
    public static final String BEARER_PREFIX = "Bearer ";

    /** 网关解析令牌后注入的用户标识 */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 网关解析令牌后注入的角色 */
    public static final String HEADER_USER_ROLE = "X-User-Role";

    /** 全链路追踪 ID */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    private SecurityConstants() {
    }
}
