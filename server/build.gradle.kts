import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.micronaut.application) apply false
    alias(libs.plugins.micronaut.library) apply false
    jacoco
}

repositories {
    mavenCentral()
}

subprojects {
    group = "gal.conxugal"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val coverageProjects = listOf(project(":domain"), project(":application"), project(":infrastructure"))
val integrationTestedProjects = listOf(project(":application"), project(":infrastructure"))

tasks.register<JacocoReport>("jacocoAggregatedReport") {
    group = "verification"
    description = "Combines unit and, where present, integration test coverage from " +
        "domain, application and infrastructure into a single report."

    val testTasks = coverageProjects.map { it.tasks.named<Test>("test") }
    val integrationTestTasks = integrationTestedProjects.map { it.tasks.named<Test>("integrationTest") }

    dependsOn(testTasks, integrationTestTasks)
    executionData.setFrom(
        coverageProjects.map { it.layout.buildDirectory.file("jacoco/test.exec") } +
            integrationTestedProjects.map { it.layout.buildDirectory.file("jacoco/integrationTest.exec") }
    )

    sourceSets(
        *coverageProjects
            .map { (it.extensions.getByName("sourceSets") as SourceSetContainer)["main"] }
            .toTypedArray()
    )

    reports {
        html.required = true
        xml.required = true
    }
}
