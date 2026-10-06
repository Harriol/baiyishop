package com.harriol.baiyishop.gateway;

import com.harriol.baiyishop.common.security.Audience;
import com.harriol.baiyishop.common.security.SecurityConstants;
import com.harriol.baiyishop.common.security.jwt.JwtProperties;
import com.harriol.baiyishop.common.security.jwt.JwtTokenProvider;
import com.harriol.baiyishop.gateway.config.GatewayAuthProperties;
import com.harriol.baiyishop.gateway.filter.JwtAuthGlobalFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关统一鉴权（REQ-1001、ADR-003、NFR-03）。
 * <p>直接测过滤器本身，不起真实服务：断言的是「边界上的安全行为」——
 * 内部接口对外 404、身份头不可伪造、公开路径放行、受保护路径必须带对的受众令牌。
 */
class JwtAuthGlobalFilterTests {

    private static final String USER_SECRET = "Hc9fhrD4XG3oUqEtkX1aejxPuL8qHrgseTO28MK8E8fJsDQU0cstViQjVQZj-ntO";
    private static final String ADMIN_SECRET = "LffxP7worjZlOi9mF5shrk0QvMYpy4eTF9tu5sjvVZlRlOHuXDa6ZKRnPllp5Sy_";

    private JwtTokenProvider tokenProvider;
    private JwtAuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setUserSecret(USER_SECRET);
        properties.setAdminSecret(ADMIN_SECRET);
        properties.setAccessTtl(Duration.ofHours(1));
        properties.setRefreshTtl(Duration.ofDays(7));
        tokenProvider = new JwtTokenProvider(properties);

        GatewayAuthProperties authProperties = new GatewayAuthProperties();
        authProperties.setPublicPaths(List.of(
                "/actuator/**",
                "/api/v1/auth/login",
                "/api/v1/products/**",
                "GET:/api/v1/seckill/activities",
                "GET:/api/v1/seckill/activities/*"));

        filter = new JwtAuthGlobalFilter(tokenProvider, authProperties, new ObjectMapper());
    }

    private record Result(HttpStatusCode status, boolean chained, ServerWebExchange exchange) {
    }

    private Result run(String method, String path, String token, String forgedUserId, String forgedRole) {
        MockServerHttpRequest.BaseBuilder<?> request =
                MockServerHttpRequest.method(HttpMethod.valueOf(method), URI.create(path));
        if (token != null) {
            request = request.header(SecurityConstants.AUTHORIZATION_HEADER, "Bearer " + token);
        }
        if (forgedUserId != null) {
            request = request.header(SecurityConstants.HEADER_USER_ID, forgedUserId);
        }
        if (forgedRole != null) {
            request = request.header(SecurityConstants.HEADER_USER_ROLE, forgedRole);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(request.build());
        AtomicReference<ServerWebExchange> passed = new AtomicReference<>();
        filter.filter(exchange, e -> {
            passed.set(e);
            return Mono.empty();
        }).block(Duration.ofSeconds(5));
        // 链路是否被放行：以「下游是否被调用」为准（写错误响应时 block() 同样返回 null）
        boolean chained = passed.get() != null;
        HttpStatusCode status = exchange.getResponse().getStatusCode() == null
                ? HttpStatus.OK : exchange.getResponse().getStatusCode();
        return new Result(status, chained, passed.get() == null ? exchange : passed.get());
    }

    @Test
    @DisplayName("内部接口对外一律 404，不暴露")
    void internalPathsAreHidden() {
        Result result = run("GET", "/internal/products/skus/1", null, null, null);
        assertThat(result.chained()).isFalse();
        assertThat(result.status()).isEqualTo(HttpStatus.NOT_FOUND);

        // 即使带了合法令牌也不放行：/internal 只允许服务间调用
        String token = tokenProvider.createAccessToken(1L, Audience.USER, null);
        assertThat(run("POST", "/internal/inventory/lock", token, null, null).status())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("公开路径无令牌放行；方法维度的规则只对该方法生效")
    void publicPathsPassThrough() {
        assertThat(run("POST", "/api/v1/auth/login", null, null, null).chained()).isTrue();
        assertThat(run("GET", "/api/v1/products/1001", null, null, null).chained()).isTrue();
        assertThat(run("GET", "/api/v1/seckill/activities", null, null, null).chained()).isTrue();
        assertThat(run("GET", "/api/v1/seckill/activities/1", null, null, null).chained()).isTrue();
        // 同一路径的 POST（抢购）不在白名单里 —— 必须登录
        assertThat(run("POST", "/api/v1/seckill/activities", null, null, null).status())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("受保护路径：无令牌 401，令牌注入身份头，且客户端伪造的身份头被剥掉")
    void protectedPathsRequireToken() {
        Result anonymous = run("GET", "/api/v1/carts", null, null, null);
        assertThat(anonymous.status()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String token = tokenProvider.createAccessToken(9527L, Audience.USER, "USER");
        Result forged = run("GET", "/api/v1/carts", token, "99999", "SUPER_ADMIN");
        assertThat(forged.chained()).isTrue();
        // 身份只认令牌里的：伪造的 X-User-Id / X-User-Role 会被移除后重新注入
        assertThat(forged.exchange().getRequest().getHeaders().getFirst(SecurityConstants.HEADER_USER_ID))
                .isEqualTo("9527");
        assertThat(forged.exchange().getRequest().getHeaders().getFirst(SecurityConstants.HEADER_USER_ROLE))
                .isEqualTo("USER");
    }

    @Test
    @DisplayName("受众按路径判定：用户令牌进不了后台接口，后台令牌进不了用户接口")
    void audienceIsCheckedByPath() {
        String userToken = tokenProvider.createAccessToken(1L, Audience.USER, "USER");
        String adminToken = tokenProvider.createAccessToken(2L, Audience.ADMIN, "OPERATOR");

        assertThat(run("GET", "/api/v1/admin/orders", userToken, null, null).status())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(run("GET", "/api/v1/carts", adminToken, null, null).status())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(run("GET", "/api/v1/admin/orders", adminToken, null, null).chained()).isTrue();

        // 非法令牌、刷新令牌都不能当访问令牌用
        assertThat(run("GET", "/api/v1/carts", "not-a-jwt", null, null).status())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        String refresh = tokenProvider.createRefreshToken(3L, Audience.USER, null);
        assertThat(run("GET", "/api/v1/carts", refresh, null, null).status())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
