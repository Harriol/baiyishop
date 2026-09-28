package com.harriol.baiyishop.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商品服务：分类、品牌、商品与 SKU、参数模板，以及首页配置（REQ-201~206、REQ-401~403）。
 * <p>模块与端口划分见 docs/architecture.md 2.2。
 */
@SpringBootApplication
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
