plugins {
    kotlin("jvm")
}

dependencies {
    // Spring Framework only (no Spring Boot Starter)
    implementation("org.springframework:spring-context:6.2.7")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
