import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.Pmd
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    java
    checkstyle
    pmd
    id("com.github.spotbugs")
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
    isIgnoreFailures = false
    isShowViolations = true
    maxWarnings = 0
}

tasks.withType<Checkstyle>().configureEach {
    exclude("**/generated/**")
}

val pmdConfigDir = rootProject.layout.projectDirectory.dir("config/pmd")

pmd {
    toolVersion = libs.findVersion("pmd").get().requiredVersion
    isConsoleOutput = true
    ruleSets = emptyList()
    ruleSetFiles = files(pmdConfigDir.file("ruleset.xml"))
}

tasks.withType<Pmd>().configureEach {
    exclude("**/generated/**")
    if (name != "pmdMain") {
        ruleSetFiles = files(pmdConfigDir.file("ruleset-test.xml"))
    }
    reports {
        html.required = true
        xml.required = true
    }
}

spotbugs {
    toolVersion = libs.findVersion("spotbugs").get().requiredVersion
    ignoreFailures = false
    showProgress = false
    effort = Effort.MAX
    excludeFilter = rootProject.layout.projectDirectory.file("config/spotbugs/exclude.xml")
}

tasks.withType<SpotBugsTask>().configureEach {
    reports.create("html")
    reports.create("xml")
}

dependencies {
    spotbugsPlugins(libs.findLibrary("findsecbugs-plugin").get())
}
