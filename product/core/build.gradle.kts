plugins {
    kotlin("jvm")
}

dependencies {
    // Spring Framework only (no Spring Boot Starter)
    implementation("org.springframework:spring-context:6.2.7")

    // Reactor Core for reactive types (Mono/Flux) in ports
    implementation("io.projectreactor:reactor-core:3.6.11")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.projectreactor:reactor-test:3.6.11")
}
