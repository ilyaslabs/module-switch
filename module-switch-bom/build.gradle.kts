plugins {
    `java-platform`
    `maven-publish`
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
            modulePom(this)
        }
    }
}
