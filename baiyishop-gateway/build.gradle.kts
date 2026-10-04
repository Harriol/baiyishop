plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Spring Cloud / Alibaba 的版本统一由版本目录提供，这里以 platform 方式导入 BOM
    implementation(platform(libs.spring.cloud.bom))
    implementation(platform(libs.spring.cloud.alibaba.bom))

    // 网关不引 common-web（那是 MVC 的），只复用 common-security 的 JWT 与密码组件
    implementation(project(":baiyishop-common-core"))
    implementation(project(":baiyishop-common-security"))

    implementation(libs.gateway.webflux)
    implementation(libs.boot.starter.json)
    // 注册中心：网关按服务名转发（lb://baiyishop-user）
    implementation(libs.nacos.discovery)
    implementation(libs.loadbalancer)

    implementation(libs.boot.actuator)

    testImplementation(libs.boot.test)
}