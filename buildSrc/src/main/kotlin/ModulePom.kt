import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

/** Shared POM metadata required by Maven Central. */
fun Project.modulePom(publication: MavenPublication) {
    publication.pom {
        name.set(project.name)
        description.set(project.description ?: "Module-Switch: switch modular-monolith modules between local, http and off")
        url.set("https://github.com/ilyaslabs/module-switch")
        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("ilyasdotdev")
                name.set("ilyas")
            }
        }
        scm {
            url.set("https://github.com/ilyaslabs/module-switch")
            connection.set("scm:git:https://github.com/ilyaslabs/module-switch.git")
            developerConnection.set("scm:git:ssh://git@github.com/ilyaslabs/module-switch.git")
        }
    }
}
