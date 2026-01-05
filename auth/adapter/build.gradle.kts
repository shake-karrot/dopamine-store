plugins {
    id("org.springframework.boot")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

dependencies {
    implementation(project(":app"))
    implementation(project(":core")) // Transitive, but explicit is sometimes clearer in clean arch

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    
    // Drivers - keeping it open or adding commonly used
    // runtimeOnly("com.mysql:mysql-connector-j") 
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
