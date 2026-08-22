import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    java
    checkstyle
    jacoco
    id("com.github.spotbugs")
    id("net.ltgt.errorprone")
    id("info.solidsoft.pitest")
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

spotbugs {
    toolVersion = libs.findVersion("spotbugs").get().requiredVersion
    ignoreFailures = false
    showProgress = false
    effort = Effort.MAX
    excludeFilter = rootProject.layout.projectDirectory.file("config/spotbugs/exclude.xml")
}

tasks.withType<SpotBugsTask>().configureEach {
    // MAX effort, with fb-contrib and findsecbugs on top, spends most of a run in GC on the
    // default worker heap. Which detectors run is unchanged.
    maxHeapSize = "2g"
    // XML only: nothing reads the HTML rendering, and producing both doubles the reporting pass.
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

pitest {
    junit5PluginVersion = libs.findVersion("pitestJunit5Plugin").get().requiredVersion
    threads = 4
    excludedClasses = listOf("*\$Introspection*", "*\$Definition*", "*\$BeanDefinition*")
    // architecture/acceptance carry no main sources, so they'd otherwise fail with
    // zero mutations; commons/domain/application/infrastructure still get a real report.
    failWhenNoMutations = false
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode = true
        excludedPaths = ".*/generated/.*"
    }
}

dependencies {
    spotbugsPlugins(libs.findLibrary("fb-contrib").get())
    spotbugsPlugins(libs.findLibrary("findsecbugs-plugin").get())
    errorprone(libs.findLibrary("errorprone-core").get())
}
