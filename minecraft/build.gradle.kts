dependencies {
    api(project(":common"))
}

tasks {
    shadowJar {
        enabled = false
    }
}
