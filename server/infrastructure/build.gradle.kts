import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("gal.conxugal.java-conventions")
    alias(libs.plugins.micronaut.library)
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.micronaut.jdbc.hikari)
    implementation(libs.micronaut.data.jdbc)
    annotationProcessor(libs.micronaut.data.processor)
    runtimeOnly(libs.postgresql)

    implementation(libs.micronaut.http.client)
    implementation(libs.jsoup)

    implementation(libs.bouncycastle.provider)

    runtimeOnly(libs.micronaut.flyway)
    runtimeOnly(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)

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

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter(libs.versions.junit.get())

            dependencies {
                implementation(project())
                implementation(project(":domain"))
                implementation(libs.micronaut.jdbc.hikari)
                implementation(libs.micronaut.test.junit5)
                implementation(libs.testcontainers.junit.jupiter)
                implementation(libs.testcontainers.postgresql)
                implementation(libs.mockito.junit.jupiter)
                implementation(libs.assertj.core)
                implementation(libs.assertj.db)
                implementation(libs.micronaut.http.client)
                implementation(libs.micronaut.serde.jackson)
                implementation(libs.wiremock.testcontainers)
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
}

configurations["integrationTestAnnotationProcessor"].extendsFrom(
    configurations["annotationProcessor"],
    configurations["testAnnotationProcessor"]
)

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("integrationTest"))
    executionData(tasks.test.get(), tasks.named("integrationTest").get())
}
