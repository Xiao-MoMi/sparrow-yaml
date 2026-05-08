java {
    withSourcesJar()
}

tasks {
    named<Jar>("jar") {
        archiveClassifier.set("plain")
    }

    named<Jar>("shadowJar") {
        archiveClassifier.set("")
    }

    named("assemble") {
        dependsOn(named("shadowJar"))
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
            artifact(tasks.named("shadowJar")) {
                builtBy(tasks.named("shadowJar"))
            }
            artifact(tasks.named("sourcesJar"))
            pom {
                name = "Sparrow Yaml"
                url = "https://github.com/Catnies/nyana-serialization"
            }
        }
    }
}
