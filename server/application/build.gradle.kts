plugins {
    id("gal.conxugal.java-conventions")
    alias(libs.plugins.micronaut.application)
}

dependencies {
    implementation(project(":domain"))

    runtimeOnly(project(":infrastructure"))

    implementation(libs.micronaut.http.server.netty)
    implementation(libs.micronaut.serde.jackson)
    implementation(libs.micronaut.security.session)
    implementation(libs.micronaut.security.csrf)
    implementation(libs.micronaut.views.thymeleaf)
    annotationProcessor(libs.micronaut.security.processor)
    runtimeOnly(libs.logback.classic)
    runtimeOnly(libs.snakeyaml)

    testImplementation(libs.micronaut.http.client)
    testImplementation(libs.micronaut.test.junit5)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
}

application {
    mainClass = "gal.conxugal.application.Application"
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("gal.conxugal.*")
    }
}

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter(libs.versions.junit.get())

            dependencies {
                implementation(project())
                implementation(project(":domain"))
                implementation(libs.micronaut.http.client)
                implementation(libs.micronaut.test.junit5)
                implementation(libs.micronaut.security.session)
                implementation(libs.micronaut.security.csrf)
                implementation(libs.mockito.junit.jupiter)
                implementation(libs.assertj.core)
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

configurations["integrationTestAnnotationProcessor"].extendsFrom(
    configurations["annotationProcessor"],
    configurations["testAnnotationProcessor"]
)
