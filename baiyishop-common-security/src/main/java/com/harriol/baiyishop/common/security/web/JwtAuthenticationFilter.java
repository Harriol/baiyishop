package com.harriol.baiyishop.common.security.web;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.Audience;
import com.harriol.baiyishop.common.security.SecurityConstants;
import com.harriol.baiyishop.common.security.context.UserContext;
import com.harriol.baiyishop.common.security.context.UserPrincipal;
import com.harriol.baiyishop.common.security.jwt.JwtTokenProvider;
import com.harriol.baiyishop.common.security.jwt.TokenBlacklistChecker;
import com.harriol.baiyishop.common.security.jwt.TokenPayload;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 服务端二次校验（docs/adr/ADR-003）：网关已校验过一遍，这里再按服务自己的密钥验一次，
 * 不直接采信网关注入的请求头。
 * <ul>
 *   <li>未带令牌：放行，由业务代码用 {@code UserContext.require()} 决定是否 401（公开接口无需登录）</li>
 *   <li>带令牌但非法 / 过期 / 受众不符 / 已注销：立即 401，避免伪造令牌被当作匿名放行</li>
 * </ul>
 * 受众按路径判定：/admin/ 路径要求后台令牌，其余要求用户令牌（docs/api.md 2.2）。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<TokenBlacklistChecker> blacklistChecker;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   ObjectMapper objectMapper,
                                   ObjectProvider<TokenBlacklistChecker> blacklistChecker) {
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
        this.blacklistChecker = blacklistChecker;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);
        log.debug("鉴权过滤器执行 uri={} 是否携带令牌={}", request.getRequestURI(), header != null);
        if (header != null && header.startsWith(SecurityConstants.BEARER_PREFIX)) {
            String token = header.substring(SecurityConstants.BEARER_PREFIX.length()).trim();
            Audience expected = request.getRequestURI().contains("/admin/") ? Audience.ADMIN : Audience.USER;
            try {
                TokenPayload payload = tokenProvider.parse(token, expected);
                if (!payload.isAccessToken()) {
                    throw new BizException(ErrorCode.UNAUTHORIZED);
                }
                TokenBlacklistChecker checker = blacklistChecker.getIfAvailable();
                if (checker != null && checker.isBlacklisted(payload.tokenId())) {
                    throw new BizException(ErrorCode.UNAUTHORIZED);
                }
                UserContext.set(new UserPrincipal(payload.userId(), payload.audience(), payload.role()));
                log.debug("令牌校验通过 userId={} aud={}", payload.userId(), payload.audience().value());
            } catch (BizException ex) {
                log.debug("令牌校验失败，返回 401：{}", ex.getMessage());
                UserContext.clear();
                writeUnauthorized(response, ex);
                return;
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse response, BizException ex) throws IOException {
        response.setStatus(ex.getErrorCode().getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(ex.getErrorCode().getCode(), ex.getMessage())));
    }
}
