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
            name = "Catnies"
            url = uri("https://repo.catnies.top/releases")
            credentials(PasswordCredentials::class)
            authentication { create<BasicAuthentication>("basic") }
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
                url = "https://github.com/Catnies/nyana-serialization"
            }
        }
    }
}
