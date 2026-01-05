plugins {
    kotlin("jvm")
}

dependencies {
    // Spring Framework only (no Spring Boot Starter)
    implementation("org.springframework:spring-context:6.2.7")

    // Reactor Core for reactive types (Mono/Flux) in ports
    implementation("io.projectreactor:reactor-core:3.6.11")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.3")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.projectreactor:reactor-test:3.6.11")
    testImplementation(kotlin("test"))
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.mockito:mockito-core:5.7.0")
}
