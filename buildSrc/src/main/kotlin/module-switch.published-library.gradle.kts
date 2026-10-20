import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar

plugins {
    id("module-switch.java-conventions")
    id("com.vanniktech.maven.publish")
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
}

mavenPublishing {
    configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = true))
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    modulePom(project)
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        versionMapping {
            usage("java-api") { fromResolutionOf("runtimeClasspath") }
            usage("java-runtime") { fromResolutionResult() }
        }
    }
}
