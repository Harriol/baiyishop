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
    // 鉴权：后台接口的角色校验与登录用户上下文
    implementation(project(":baiyishop-common-security"))

    // 注册中心：服务在 Nacos 可见（REQ-1002）
    implementation(libs.nacos.discovery)

    implementation(libs.boot.actuator)

    // 数据库：驱动 + Flyway 迁移（docs/database.md 1.6）
    runtimeOnly(libs.mysql.connector.j)
    implementation(libs.boot.starter.flyway)
    runtimeOnly(libs.flyway.mysql)

    testImplementation(libs.boot.test)
}
