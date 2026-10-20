plugins {
    id("module-switch.java-conventions")
}

description = "End-to-end tests: monolith and split deployments in one JVM. Not published."

dependencies {
    testImplementation(project(":module-switch-spring-boot-starter"))
    testImplementation(project(":module-switch-test"))
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework:spring-web")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("io.micrometer:micrometer-observation-test")
}
