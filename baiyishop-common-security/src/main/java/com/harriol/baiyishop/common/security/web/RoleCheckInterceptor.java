package com.harriol.baiyishop.common.security.web;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.security.annotation.RequiresRole;
import com.harriol.baiyishop.common.security.context.UserContext;
import com.harriol.baiyishop.common.security.context.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 角色校验拦截器：读取 {@link RequiresRole} 并比对当前身份。
 * <ul>
 *   <li>无身份 → 401（前置过滤器已挡掉非法令牌，这里兜住漏配的路径）</li>
 *   <li>有身份但非后台令牌，或角色不在允许列表 → 403</li>
 * </ul>
 * 权限校验放在服务端、不信任前端（NFR-03）。
 */
public class RoleCheckInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequiresRole annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), RequiresRole.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequiresRole.class);
        }
        if (annotation == null) {
            return true;
        }

        UserPrincipal principal = UserContext.get();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!principal.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        boolean allowed = principal.role() != null
                && Arrays.stream(annotation.value()).anyMatch(role -> role.equals(principal.role()));
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return true;
    }
}