import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Project

/** Shared POM metadata required by Maven Central. */
fun MavenPublishBaseExtension.modulePom(project: Project) {
    pom {
        name.set(project.name)
        description.set(project.provider { project.description ?: "Module-Switch: switch modular-monolith modules between local, http and off" })
        inceptionYear.set("2026")
        url.set("https://github.com/ilyaslabs/module-switch")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("ilyaslabs")
                name.set("Muhammad ilyas")
                email.set("m.ilyas@live.com")
                organizationUrl.set("https://ilyaslabs.github.io/")
            }
        }
        scm {
            url.set("https://github.com/ilyaslabs/module-switch")
            connection.set("scm:git:https://github.com/ilyaslabs/module-switch.git")
            developerConnection.set("scm:git:ssh://git@github.com/ilyaslabs/module-switch.git")
        }
        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/ilyaslabs/module-switch/issues")
        }
    }
}
