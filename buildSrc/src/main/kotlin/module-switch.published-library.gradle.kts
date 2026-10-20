import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar

plugins {
    id("module-switch.java-conventions")
    id("module-switch.central-publishing")
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
}

mavenPublishing {
    configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = true))
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        versionMapping {
            usage("java-api") { fromResolutionOf("runtimeClasspath") }
            usage("java-runtime") { fromResolutionResult() }
        }
    }
}
