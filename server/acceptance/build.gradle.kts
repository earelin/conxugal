plugins {
    id("gal.conxugal.java-conventions")
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(libs.rest.assured)
    testImplementation(libs.assertj.core)
    testImplementation(libs.postgresql)

    testImplementation(libs.wiremock)
    testImplementation(libs.playwright)
}

tasks.named("test") {
    enabled = false
}

// Not wired as a dependency of `acceptance`: it reaches the network, and local runs
// already rely on Playwright's implicit browser-binary download on first use. CI
// invokes it explicitly so the browser is in place before the suite starts.
// No --with-deps: ubuntu-latest already carries every library headless Chromium needs,
// and the packages it adds are fonts this text-assertion-only suite never renders —
// fonts a cache restore can never cover, so they would be re-fetched on every run.
tasks.register<JavaExec>("installPlaywrightBrowsers") {
    group = "verification"
    description = "Installs Playwright's browser binaries."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
    args("install", "chromium")
}

tasks.register<Test>("acceptance") {
    group = "verification"
    description = "Black-box acceptance tests against a running application instance."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    listOf("app.baseUrl", "db.url", "db.username", "db.password").forEach { property ->
        System.getProperty(property)?.let { systemProperty(property, it) }
    }
    outputs.cacheIf { false }
    outputs.upToDateWhen { false }
}
