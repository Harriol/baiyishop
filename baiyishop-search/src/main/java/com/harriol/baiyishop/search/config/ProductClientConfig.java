package com.harriol.baiyishop.search.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 调用 product-service 的客户端（docs/architecture.md 4.1）。
 * <p>{@code @LoadBalanced} 让 {@code http://baiyishop-product} 这类服务名由 Nacos 实例列表解析，
 * 与 Feign 等价但少一层依赖；超时在 source 里按约定设置（连接 500ms / 读取 2000ms）。
 */
@Configuration
public class ProductClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
