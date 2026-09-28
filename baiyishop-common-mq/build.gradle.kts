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

    // 消息与分布式事务：RocketMQ 本地消息表 + Seata AT（docs/adr/ADR-002、ADR-005、ADR-008）
    api(libs.rocketmq.spring.boot.starter)
    api(libs.seata.spring.boot.starter)

    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
}
