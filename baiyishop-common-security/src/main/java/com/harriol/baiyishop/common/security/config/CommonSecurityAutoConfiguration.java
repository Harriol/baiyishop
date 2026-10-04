package com.harriol.baiyishop.common.security.config;

import tools.jackson.databind.ObjectMapper;
import com.harriol.baiyishop.common.security.jwt.JwtProperties;
import com.harriol.baiyishop.common.security.jwt.JwtTokenProvider;
import com.harriol.baiyishop.common.security.web.JwtAuthenticationFilter;
import com.harriol.baiyishop.common.security.web.RoleCheckInterceptor;
import com.harriol.baiyishop.common.security.jwt.TokenBlacklistChecker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 公共安全能力自动装配：JWT 签发校验、密码编码器、服务端二次校验过滤器。
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenProvider jwtTokenProvider(JwtProperties properties) {
        return new JwtTokenProvider(properties);
    }

    /** 密码一律 BCrypt 加盐哈希，禁止明文与 MD5（REQ-101、REQ-105） */
    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                                           ObjectMapper objectMapper,
                                                           ObjectProvider<TokenBlacklistChecker> blacklistChecker) {
        return new JwtAuthenticationFilter(tokenProvider, objectMapper, blacklistChecker);
    }

    /** 注册后台角色校验拦截器（只影响带 @RequiresRole 的接口） */
    @Bean
    public WebMvcConfigurer roleCheckWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new RoleCheckInterceptor()).addPathPatterns("/**");
            }
        };
    }
}
