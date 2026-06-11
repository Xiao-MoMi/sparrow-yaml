plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.4.1"
    id("maven-publish")
}

dependencies {
    implementation(project(":common"))
}

allprojects {
    // 插件
    apply(plugin = "java-library")
    apply(plugin = "com.gradleup.shadow")
    apply(plugin = "maven-publish")

    // 属性
    version = "1.0.4"
    java.sourceCompatibility = JavaVersion.VERSION_17
    java.targetCompatibility = JavaVersion.VERSION_17

    // 仓库
    repositories {
        mavenCentral()
        mavenLocal()
    }

    // 依赖
    dependencies {
        compileOnly("org.ow2.asm:asm:9.7")
        compileOnly("org.jetbrains:annotations:26.0.2")
        implementation(rootProject.files("libs/snakeyaml-engine-3.1-SNAPSHOT-forked.jar"))

        testImplementation("org.joml:joml:1.10.8")
        testImplementation("org.ow2.asm:asm:9.7")
        testImplementation("com.google.code.gson:gson:2.13.2")
        testImplementation("org.jetbrains:annotations:26.0.2")
        testImplementation(rootProject.files("libs/snakeyaml-engine-3.1-SNAPSHOT-forked.jar"))
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
        relocate("org.snakeyaml.engine", "net.momirealms.sparrow.libs.snakeyaml.engine")
    }
}

// 版本标识
fun versionBanner(): String = project.providers.exec {
    commandLine("git", "rev-parse", "--short=8", "HEAD")
}.standardOutput.asText.map { it.trim() }.getOrElse("Unknown")
