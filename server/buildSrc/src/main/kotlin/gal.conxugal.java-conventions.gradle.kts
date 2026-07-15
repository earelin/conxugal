import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    java
    checkstyle
    pmd
    jacoco
    id("com.github.spotbugs")
    id("net.ltgt.errorprone")
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

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

tasks.withType<JacocoReport>().configureEach {
    reports {
        html.required = true
        xml.required = true
    }
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode = true
        excludedPaths = ".*/generated/.*"
    }
}

dependencies {
    spotbugsPlugins(libs.findLibrary("findsecbugs-plugin").get())
    errorprone(libs.findLibrary("errorprone-core").get())
}
