rootProject.name = "baiyishop"

// 依赖仓库统一在 settings 中声明，子模块不各自配置，避免版本来源分散
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

// 公共模块（docs/architecture.md 10.3）
listOf("core", "web", "security", "data", "mq").forEach {
    include("baiyishop-common-$it")
}

// 业务服务（docs/architecture.md 2.2：8 个业务服务）
listOf("gateway", "user", "product", "search", "inventory", "order", "payment", "seckill").forEach {
    include("baiyishop-$it")
}
