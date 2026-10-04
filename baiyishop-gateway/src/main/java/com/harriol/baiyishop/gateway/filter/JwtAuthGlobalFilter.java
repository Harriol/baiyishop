package com.harriol.baiyishop.gateway.filter;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.Audience;
import com.harriol.baiyishop.common.security.SecurityConstants;
import com.harriol.baiyishop.common.security.jwt.JwtTokenProvider;
import com.harriol.baiyishop.common.security.jwt.TokenPayload;
import com.harriol.baiyishop.gateway.config.GatewayAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关统一鉴权（REQ-1001、docs/api.md 第 2 章、docs/adr/ADR-003）。
 * <ul>
 *   <li>{@code /internal/**} 是服务间接口，对外一律 404，不暴露</li>
 *   <li>客户端自带的 X-User-Id / X-User-Role 一律剥掉，身份只能由网关按令牌注入，防伪造</li>
 *   <li>公开路径直接放行；受保护路径必须带有效令牌，否则 401</li>
 *   <li>受众按路径判定：{@code /api/v1/admin/**} 要求后台令牌，其余要求用户令牌</li>
 * </ul>
 * 服务端仍会用自己的密钥再校验一次（双重校验，ADR-003），网关不是唯一防线。
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);

    private final JwtTokenProvider tokenProvider;
    private final GatewayAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthGlobalFilter(JwtTokenProvider tokenProvider,
                               GatewayAuthProperties properties,
                               ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (path.startsWith("/internal/")) {
            log.warn("拦截对外访问的内部接口 path={}", path);
            return writeError(exchange, ErrorCode.NOT_FOUND);
        }

        // 无论何种路径，先剥掉客户端自带的身份头
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(SecurityConstants.HEADER_USER_ID);
                    headers.remove(SecurityConstants.HEADER_USER_ROLE);
                })
                .build();
        ServerWebExchange cleaned = exchange.mutate().request(sanitized).build();

        if (isPublic(exchange.getRequest().getMethod(), path)) {
            return chain.filter(cleaned);
        }

        String header = sanitized.getHeaders().getFirst(SecurityConstants.AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return writeError(cleaned, ErrorCode.UNAUTHORIZED);
        }

        try {
            Audience expected = path.startsWith("/api/v1/admin/") ? Audience.ADMIN : Audience.USER;
            TokenPayload payload = tokenProvider.parse(header.substring(SecurityConstants.BEARER_PREFIX.length()).trim(), expected);
            if (!payload.isAccessToken()) {
                throw new BizException(ErrorCode.UNAUTHORIZED);
            }
            ServerHttpRequest authenticated = cleaned.getRequest().mutate()
                    .header(SecurityConstants.HEADER_USER_ID, String.valueOf(payload.userId()))
                    .header(SecurityConstants.HEADER_USER_ROLE, payload.role() == null ? "" : payload.role())
                    .build();
            return chain.filter(cleaned.mutate().request(authenticated).build());
        } catch (BizException ex) {
            log.debug("网关鉴权失败 path={} reason={}", path, ex.getMessage());
            return writeError(cleaned, ex.getErrorCode());
        }
    }

    private boolean isPublic(HttpMethod method, String path) {
        List<String> rules = properties.getPublicPaths();
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        for (String rule : rules) {
            int colon = rule.indexOf(':');
            if (colon > 0) {
                String ruleMethod = rule.substring(0, colon);
                String rulePath = rule.substring(colon + 1);
                if (method != null && ruleMethod.equalsIgnoreCase(method.name()) && pathMatcher.match(rulePath, path)) {
                    return true;
                }
            } else if (pathMatcher.match(rule, path)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> writeError(ServerWebExchange exchange, ErrorCode errorCode) {
        exchange.getResponse().setStatusCode(HttpStatus.valueOf(errorCode.getHttpStatus()));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(Result.fail(errorCode));
        } catch (Exception e) {
            bytes = "{\"code\":10006,\"message\":\"系统繁忙，请稍后重试\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 在 traceId 之后、路由转发之前
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}