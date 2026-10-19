plugins {
    id("module-switch.published-library")
}

description = "Module-Switch runtime: mode resolution, scan filtering, startup checks, local wrapper, HTTP client and internal server."

dependencies {
    api(project(":module-switch-api"))
    api("org.springframework.boot:spring-boot")
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("io.micrometer:micrometer-observation")
    implementation("tools.jackson.core:jackson-databind")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    compileOnly("org.springframework:spring-web")
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework:spring-web")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("io.micrometer:micrometer-observation-test")
}
