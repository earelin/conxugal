plugins {
    id("gal.conxugal.java-conventions")
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(project(":domain"))
    testImplementation(project(":application"))
    testImplementation(project(":infrastructure"))

    testImplementation(libs.micronaut.http.server.netty)
    testImplementation(libs.archunit.junit5)
}

tasks.test {
    useJUnitPlatform()
}
