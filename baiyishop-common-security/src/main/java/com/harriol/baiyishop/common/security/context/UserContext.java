package com.harriol.baiyishop.common.security.context;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;

/**
 * 请求级身份上下文，由 {@code JwtAuthenticationFilter} 写入、请求结束清理（ThreadLocal）。
 * <p>业务代码通过 {@link #userId()} 取当前用户，**不接受前端传入的 userId**（NFR-03）。
 */
public final class UserContext {

    private static final ThreadLocal<UserPrincipal> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(UserPrincipal principal) {
        HOLDER.set(principal);
    }

    public static UserPrincipal get() {
        return HOLDER.get();
    }

    /** 取当前身份，未登录抛 401 语义异常 */
    public static UserPrincipal require() {
        UserPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    /** 取当前用户 ID，未登录抛 401 语义异常 */
    public static long requireUserId() {
        return require().userId();
    }

    /** 取当前后台管理员 ID，并要求是后台令牌 */
    public static long requireAdminId() {
        UserPrincipal principal = require();
        if (!principal.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return principal.userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
