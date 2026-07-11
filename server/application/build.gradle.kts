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
                implementation(libs.micronaut.test.rest.assured)
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

val uiDir = layout.projectDirectory.dir("../../ui")
val generatedWebResourcesDir = layout.buildDirectory.dir("generated/ui")

val npmCi = tasks.register<Exec>("npmCi") {
    group = "node"
    description = "Installs the UI's dependencies with npm ci."
    workingDir(uiDir)
    inputs.file(uiDir.file("package.json"))
    inputs.file(uiDir.file("package-lock.json"))
    outputs.dir(uiDir.dir("node_modules"))
    commandLine("npm", "ci")
}

val npmBuild = tasks.register<Exec>("npmBuild") {
    group = "node"
    description = "Builds the UI's static assets with npm run build."
    dependsOn(npmCi)
    workingDir(uiDir)
    inputs.dir(uiDir.dir("src"))
    inputs.file(uiDir.file("index.html"))
    inputs.file(uiDir.file("vite.config.ts"))
    inputs.file(uiDir.file("tsconfig.json"))
    inputs.file(uiDir.file("package.json"))
    outputs.dir(uiDir.dir("dist"))
    commandLine("npm", "run", "build")
}

val copyUiDist = tasks.register<Copy>("copyUiDist") {
    description = "Copies the UI's built static assets onto the application's runtime classpath, " +
        "under a public/ classpath root Micronaut's static-resources config maps at /."
    dependsOn(npmBuild)
    from(uiDir.dir("dist"))
    into(generatedWebResourcesDir.map { it.dir("public") })
}

sourceSets {
    main {
        output.dir(mapOf("builtBy" to copyUiDist), generatedWebResourcesDir)
    }
}
