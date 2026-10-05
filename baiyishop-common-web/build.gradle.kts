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

    // 内部服务调用（RestClient + @LoadBalanced）；各服务由 nacos-discovery 带入负载均衡器，
    // 公共模块只做编译期依赖，运行期缺失时自动装配整体退让。
    // 版本由 Spring Cloud BOM 提供，故这里以 platform 方式显式引入 BOM。
    compileOnly(platform(libs.spring.cloud.bom))
    compileOnly(libs.loadbalancer)

    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
}
