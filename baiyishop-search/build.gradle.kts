plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Spring Cloud / Alibaba 的版本统一由版本目录提供，这里以 platform 方式导入 BOM
    implementation(platform(libs.spring.cloud.bom))
    implementation(platform(libs.spring.cloud.alibaba.bom))

    implementation(project(":baiyishop-common-web"))
    // 注册中心：服务在 Nacos 可见（REQ-1002）
    implementation(libs.nacos.discovery)
    // 负载均衡：全量重建时按服务名调用 product-service（lb://baiyishop-product）
    implementation(libs.loadbalancer)

    // 检索存储：Elasticsearch（REQ-301，docs/architecture.md 5.5）
    // 用官方 Java API 客户端直连，不引 spring-data-elasticsearch：后者在 Boot 4 下绑定 9.x 客户端，
    // 与部署的 8.11.3 服务器不兼容（见 libs.versions.toml 说明）。
    implementation(libs.elasticsearch.java)
    // 消费幂等：search-service 无 MySQL schema，按 docs/database.md 9.3 用 Redis 去重
    implementation(libs.boot.data.redis)
    // 消息：消费 product-service 的 baiyishop-product-changed（ADR-005）
    implementation(libs.rocketmq.spring.boot.starter)

    implementation(libs.boot.actuator)

    testImplementation(libs.boot.test)
}
