plugins {
    id("module-switch.published-library")
}

description = "Module-Switch Spring Boot auto-configuration."

dependencies {
    api(project(":module-switch-core"))
    api("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot-jackson")

    testImplementation("org.springframework.boot:spring-boot-test")
}
