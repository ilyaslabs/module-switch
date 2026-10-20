import com.vanniktech.maven.publish.JavaPlatform

plugins {
    `java-platform`
    id("module-switch.central-publishing")
}

description = "Module-Switch bill of materials."

dependencies {
    constraints {
        api(project(":module-switch-api"))
        api(project(":module-switch-core"))
        api(project(":module-switch-spring-boot-autoconfigure"))
        api(project(":module-switch-spring-boot-starter"))
        api(project(":module-switch-test"))
    }
}

mavenPublishing {
    configure(JavaPlatform())
}
