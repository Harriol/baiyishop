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

    // 持久层：MyBatis-Plus（docs/adr/ADR-006 持久层选型）
    api(libs.mybatis.plus.spring.boot4.starter)
    api(libs.mybatis.plus.jsqlparser)

    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
}
