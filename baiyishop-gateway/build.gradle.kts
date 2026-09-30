plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Spring Cloud / Alibaba 的版本统一由版本目录提供，这里以 platform 方式导入 BOM
    implementation(platform(libs.spring.cloud.bom))
    implementation(platform(libs.spring.cloud.alibaba.bom))

    implementation(project(":baiyishop-common-core"))
    implementation(libs.gateway.webflux)
    // 注册中心：服务在 Nacos 可见（REQ-1002）
    implementation(libs.nacos.discovery)

    implementation(libs.boot.actuator)

    testImplementation(libs.boot.test)
}
