plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Spring Cloud / Alibaba 的版本统一由版本目录提供，这里以 platform 方式导入 BOM
    implementation(platform(libs.spring.cloud.bom))
    implementation(platform(libs.spring.cloud.alibaba.bom))

    implementation(project(":baiyishop-common-web"))
    // 持久层：MyBatis-Plus 基础配置 + 防全表更新拦截（ADR-006）
    implementation(project(":baiyishop-common-data"))
    // 鉴权：抢购与结果查询需要登录用户上下文
    implementation(project(":baiyishop-common-security"))

    // 注册中心：服务在 Nacos 可见（REQ-1002）
    implementation(libs.nacos.discovery)
    // 负载均衡：按服务名调用 inventory / product / order
    implementation(libs.loadbalancer)
    // 秒杀预扣：Redis 原子脚本（ADR-008）
    implementation(libs.boot.data.redis)
    // 异步下单：预扣成功写本地消息表，投递给 order-service
    implementation(libs.rocketmq.spring.boot.starter)

    implementation(libs.boot.actuator)

    // 数据库：驱动 + Flyway 迁移（docs/database.md 1.6）
    runtimeOnly(libs.mysql.connector.j)
    implementation(libs.boot.starter.flyway)
    runtimeOnly(libs.flyway.mysql)

    testImplementation(libs.boot.test)
}
