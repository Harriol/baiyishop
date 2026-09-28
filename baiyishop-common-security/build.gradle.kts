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

    compileOnly(libs.boot.webmvc)
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
}
