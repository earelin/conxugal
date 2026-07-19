plugins {
    id("gal.conxugal.java-conventions")
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(libs.rest.assured)
    testImplementation(libs.assertj.core)

    testImplementation(libs.wiremock)
    testImplementation(libs.playwright)
}

tasks.named("test") {
    enabled = false
}

// Not wired as a dependency of `acceptance`: it installs OS-level packages via sudo,
// which CI runners have passwordless but local dev machines may not. Local runs keep
// relying on Playwright's implicit browser-binary download on first use; CI invokes
// this task explicitly before `acceptance` to also get the OS deps headless Chromium
// needs on a bare runner.
tasks.register<JavaExec>("installPlaywrightBrowsers") {
    group = "verification"
    description = "Installs Playwright's browser binaries and OS dependencies."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
    args("install", "--with-deps", "chromium")
}

tasks.register<Test>("acceptance") {
    group = "verification"
    description = "Black-box acceptance tests against a running application instance."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    if (System.getProperty("app.baseUrl") != null) {
        systemProperty("app.baseUrl", System.getProperty("app.baseUrl"))
    }
    outputs.cacheIf { false }
    outputs.upToDateWhen { false }
}
