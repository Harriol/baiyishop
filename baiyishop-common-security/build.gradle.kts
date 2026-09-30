plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    api(project(":baiyishop-common-core"))

    // JWT（docs/adr/ADR-003 鉴权方案）
    api(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // 密码加盐哈希：只用 BCrypt 实现，不引入完整 Spring Security（最小依赖原则）
    api(libs.spring.security.crypto)

    compileOnly(libs.boot.webmvc)
    compileOnly(libs.boot.starter.json)
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
}
