package com.harriol.baiyishop.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API 网关：路由转发、JWT 鉴权、限流与统一入口（REQ-1001）。
 * <p>模块与端口划分见 docs/architecture.md 2.2。
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
