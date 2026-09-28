plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

allprojects {
    group = "com.harriol.baiyishop"
    version = "0.0.1-SNAPSHOT"
}

// 所有子模块共用的编译与测试约定（业务模块在各自 build 文件中再应用 Spring Boot 插件）
subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion = JavaLanguageVersion.of(21) }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    dependencies {
        // 所有模块统一用 Spring Boot BOM 管理版本（版本号来自 libs.versions.toml）
        add("annotationProcessor", rootProject.libs.lombok)
        add("compileOnly", rootProject.libs.lombok)
        add("testAnnotationProcessor", rootProject.libs.lombok)
        add("testCompileOnly", rootProject.libs.lombok)
    }
}
