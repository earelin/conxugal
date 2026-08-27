import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("gal.conxugal.java-conventions")
    alias(libs.plugins.micronaut.library)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":commons"))

    implementation(libs.micronaut.jdbc.hikari)
    implementation(libs.micronaut.data.jdbc)
    annotationProcessor(libs.micronaut.data.processor)
    runtimeOnly(libs.postgresql)

    implementation(libs.micronaut.http.client)
    implementation(libs.micronaut.serde.jackson)
    implementation(libs.jsoup)

    implementation(platform(libs.resilience4j.bom))
    implementation(libs.resilience4j.retry)
    implementation(libs.resilience4j.ratelimiter)
    implementation(libs.resilience4j.circuitbreaker)

    implementation(libs.bouncycastle.provider)

    runtimeOnly(libs.micronaut.flyway)
    runtimeOnly(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.junit.jupiter)
}

micronaut {
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("gal.conxugal.infrastructure.*")
    }
}

// The pages this source really served, byte for byte. Both suites read them — the unit tests
// parse them off the classpath, the integration test serves the same bytes through WireMock — so
// they sit outside both source sets rather than one borrowing from the other's resources.
val capturedSource = layout.projectDirectory.dir("src/capturedSource")

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter(libs.versions.junit.get())

            sources {
                resources.srcDir(capturedSource)
            }

            dependencies {
                implementation(project())
                implementation(project(":domain"))
                implementation(libs.micronaut.jdbc.hikari)
                implementation(libs.micronaut.data.jdbc)
                implementation(libs.micronaut.test.junit5)
                implementation(libs.testcontainers.junit.jupiter)
                implementation(libs.testcontainers.postgresql)
                implementation(libs.flyway.core)
                implementation(libs.logback.classic)
                implementation(libs.mockito.junit.jupiter)
                implementation(libs.assertj.core)
                implementation(libs.assertj.db)
                implementation(libs.micronaut.http.client)
                implementation(libs.micronaut.serde.jackson)
                implementation(libs.jsoup)
                implementation(libs.wiremock.testcontainers)
                implementation(libs.wiremock.standalone)
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(tasks.test)
                    }
                }
            }
        }
    }
}

val generatedVersionDir = layout.buildDirectory.dir("generated/version")

val generateVersionProperties = tasks.register("generateVersionProperties") {
    description = "Writes the project's version onto the runtime classpath, for the system-status probe."
    val outputFile = generatedVersionDir.map { it.file("conxugal-version.properties") }
    val projectVersion = project.version.toString()
    outputs.file(outputFile)
    doLast {
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("version=$projectVersion\n")
        }
    }
}

sourceSets {
    main {
        resources.srcDir(files(generatedVersionDir).builtBy(generateVersionProperties))
    }
    test {
        resources.srcDir(capturedSource)
    }
}

configurations["integrationTestAnnotationProcessor"].extendsFrom(
    configurations["annotationProcessor"],
    configurations["testAnnotationProcessor"]
)

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("integrationTest"))
    executionData(tasks.test.get(), tasks.named("integrationTest").get())
}
