plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":adapter"))

    // Spring Boot for worker application
    implementation("org.springframework.boot:spring-boot-starter")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
