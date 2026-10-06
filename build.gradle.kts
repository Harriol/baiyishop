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
    // 覆盖率：docs/项目开发文档.md 7.5 要求核心模块 ≥80%、整体 ≥60%
    apply(plugin = "jacoco")

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion = JavaLanguageVersion.of(21) }
    }

    extensions.configure<JacocoPluginExtension> {
        toolVersion = rootProject.libs.versions.jacoco.get()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // 跑完测试直接产覆盖率报告（jacocoTestReport 依赖 test）
        finalizedBy(tasks.withType<JacocoReport>())
    }

    tasks.withType<JacocoReport>().configureEach {
        dependsOn(tasks.withType<Test>())
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }

    dependencies {
        // 所有模块统一用 Spring Boot BOM 管理版本（版本号来自 libs.versions.toml）
        add("annotationProcessor", rootProject.libs.lombok)
        add("compileOnly", rootProject.libs.lombok)
        add("testAnnotationProcessor", rootProject.libs.lombok)
        add("testCompileOnly", rootProject.libs.lombok)
    }
}
