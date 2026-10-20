plugins {
    id("module-switch.published-library")
}

description = "Module-Switch Spring Boot starter."

dependencies {
    api(project(":module-switch-spring-boot-autoconfigure"))
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-jackson")
}
