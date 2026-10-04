package com.harriol.baiyishop.gateway.config;

import com.harriol.baiyishop.common.security.jwt.JwtProperties;
import com.harriol.baiyishop.common.security.jwt.JwtTokenProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关侧的 JWT 组件。复用 common-security 的实现，保证「令牌格式只有一处定义」。
 * <p>common-security 的自动装配限定为 servlet 应用，网关是 WebFlux，故在此手工声明。
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, GatewayAuthProperties.class})
public class GatewaySecurityConfig {

    @Bean
    public JwtTokenProvider jwtTokenProvider(JwtProperties properties) {
        return new JwtTokenProvider(properties);
    }
}