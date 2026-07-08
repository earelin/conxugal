import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    java
    checkstyle
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

checkstyle {
    toolVersion = libs.findVersion("checkstyle").get().requiredVersion
    configDirectory = rootProject.layout.projectDirectory.dir("config/checkstyle")
    isIgnoreFailures = true
    isShowViolations = true
    maxWarnings = Int.MAX_VALUE
}

tasks.withType<Checkstyle>().configureEach {
    configProperties?.put("org.checkstyle.google.severity", "warning")
    exclude("**/generated/**")
}
