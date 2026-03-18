plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.2"
}

allprojects {
    // 插件
    apply(plugin = "java")
    apply(plugin = "com.gradleup.shadow")

    // 属性
    val version = "1.0.0"
    java.sourceCompatibility = JavaVersion.VERSION_17
    java.targetCompatibility = JavaVersion.VERSION_17

    // 仓库
    repositories {
        mavenCentral()
        mavenLocal()
    }

    // 依赖
    dependencies {
        compileOnly("org.jetbrains:annotations:26.0.2")
        // implementation("org.snakeyaml:snakeyaml-engine:3.0.1")
        implementation(rootProject.files("libs/snakeyaml-engine-3.1-SNAPSHOT-forked.jar"))

        testImplementation(platform("org.junit:junit-bom:5.10.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    // 测试
    tasks {
        test {
            useJUnitPlatform()
        }
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        archiveFileName = "${rootProject.name}-${versionBanner()}.jar"
        // 重定位
        relocate("org.snakeyaml.engine", "net.momirealms.sparrow.libs.org.snakeyaml.engine")
    }
}

// 版本标识
fun versionBanner(): String = project.providers.exec {
    commandLine("git", "rev-parse", "--short=8", "HEAD")
}.standardOutput.asText.map { it.trim() }.getOrElse("Unknown")