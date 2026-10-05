package com.harriol.baiyishop.common.web.config;

import com.harriol.baiyishop.common.web.client.InternalApiClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * 内部服务调用的自动装配（docs/architecture.md 4.1）。
 * <p>只在引入了负载均衡器（Nacos discovery 会带进来）的服务里生效：
 * 公共模块不能强制所有服务都依赖 spring-cloud-loadbalancer。
 */
// 必须排在 Boot 自己的 RestClientAutoConfiguration 之前：它的 RestClient.Builder 也标了
// @ConditionalOnMissingBean，谁先注册谁生效；我们要的是「带负载均衡的构建器」。
@AutoConfiguration(beforeName = "org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration")
@ConditionalOnClass({RestClient.class, LoadBalanced.class})
@ConditionalOnProperty(name = "baiyishop.internal.enabled", havingValue = "true", matchIfMissing = true)
public class InternalClientAutoConfiguration {

    /** 按服务名寻址的构建器（lb://baiyishop-xxx 由 Nacos 实例列表解析） */
    @Bean
    @LoadBalanced
    @ConditionalOnMissingBean(RestClient.Builder.class)
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @ConditionalOnMissingBean
    public InternalApiClients internalApiClients(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${spring.application.name:unknown}") String serviceName,
            @Value("${baiyishop.internal.connect-timeout-ms:500}") long connectTimeoutMs,
            @Value("${baiyishop.internal.read-timeout-ms:2000}") long readTimeoutMs) {
        return new InternalApiClients(restClientBuilder, objectMapper, serviceName,
                Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(readTimeoutMs));
    }
}
