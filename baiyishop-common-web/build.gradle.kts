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
    api(libs.boot.webmvc)
    api(libs.boot.validation)
    // Boot 4 的 webmvc starter 不含 Jackson，这里统一补齐，避免各服务各自踩坑
    api(libs.boot.starter.json)

    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
}
