java {
    withSourcesJar()
}

tasks {
    shadowJar {
        archiveClassifier = ""
        relocate("org.snakeyaml.engine", "net.momirealms.sparrow.yaml.libs.snakeyaml.engine")
    }
}

publishing {
    repositories {
        maven {
            name = "XiaoMoMi"
            url = uri("https://repo.momirealms.net/releases")
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = "net.momirealms"
            artifactId = "sparrow-yaml"
            version = version
            from(components["shadow"])
            artifact(tasks["sourcesJar"])
            pom {
                name = "Sparrow Yaml"
                url = "https://github.com/Xiao-MoMi/sparrow-yaml"
            }
        }
    }
}
