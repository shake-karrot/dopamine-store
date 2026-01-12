plugins {
    kotlin("jvm")
}

dependencies {
    // Spring Framework only (no Spring Boot Starter)
    implementation("org.springframework:spring-context:6.2.7")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
