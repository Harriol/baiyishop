package com.harriol.baiyishop.common.web.config;

import com.harriol.baiyishop.common.web.filter.TraceIdFilter;
import com.harriol.baiyishop.common.web.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 公共 Web 能力的自动装配：服务引入 common-web 即生效，无需各自复制配置（NFR-05）。
 * <p>注册入口见 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /** Filter 类型的 Bean 会被 Spring Boot 自动注册到全部请求，并遵循类上的 @Order */
    @Bean
    @ConditionalOnMissingBean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }
}
