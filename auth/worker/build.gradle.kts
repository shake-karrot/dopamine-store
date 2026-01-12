plugins {
    id("org.springframework.boot")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":app"))
    implementation("org.springframework.boot:spring-boot-starter")
}
