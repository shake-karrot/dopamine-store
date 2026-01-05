plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

repositories {
    mavenCentral()
    maven { url = uri("https://packages.confluent.io/maven/") }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}")
    }
}

dependencies {
    implementation(project(":core"))

    // Spring Boot Starters for external integrations
    implementation("org.springframework.boot:spring-boot-starter")

    // R2DBC for reactive database access
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.postgresql:r2dbc-postgresql")
    implementation("io.r2dbc:r2dbc-pool")

    // Flyway for database migrations
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Redis for caching and distributed state
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("io.lettuce:lettuce-core")

    // Kafka for event publishing
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.avro:avro:1.11.3")
    implementation("io.confluent:kafka-avro-serializer:7.5.0")

    // WebFlux for reactive web (needed for WebFilter in TracingConfig)
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Apache Commons Pool2 for Redis connection pooling
    implementation("org.apache.commons:commons-pool2:2.11.1")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}

// Avro code generation configuration
sourceSets {
    main {
        java {
            srcDir("${projectDir}/../shared/events")
        }
    }
}

tasks.withType<com.github.davidmc24.gradle.plugin.avro.GenerateAvroJavaTask> {
    source("${projectDir}/../shared/events/product")
    setOutputDir(file("${buildDir}/generated-main-avro-java"))
}

// Ensure generated Avro classes are compiled
tasks.named("compileKotlin") {
    dependsOn("generateAvroJava")
}

sourceSets["main"].java {
    srcDir("${buildDir}/generated-main-avro-java")
}
