plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val springBootBom = extensions.getByType<VersionCatalogsExtension>().named("libs").findLibrary("spring-boot-dependencies").get()

// The Spring Boot BOM only aligns versions at build time. It sits in its own configuration so it
// never reaches published metadata; published POMs carry the resolved versions instead.
val versionAlignment = configurations.create("versionAlignment") {
    isCanBeConsumed = false
    isCanBeResolved = false
}
configurations.matching {
    it.name in setOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath",
        "annotationProcessor")
}.configureEach { extendsFrom(versionAlignment) }

dependencies {
    versionAlignment(platform(springBootBom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial,-try"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-params")
}
