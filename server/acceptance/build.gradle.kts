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

tasks.register<Test>("acceptance") {
    group = "verification"
    description = "Black-box acceptance tests against a running application instance."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    if (System.getProperty("app.baseUrl") != null) {
        systemProperty("app.baseUrl", System.getProperty("app.baseUrl"))
    }
}
