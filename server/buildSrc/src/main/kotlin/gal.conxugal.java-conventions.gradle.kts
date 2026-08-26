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
    reports.create("html")
    reports.create("xml")
    // Production sources only. On test sources the analysis reports on JUnit/AssertJ/Mockito
    // idioms rather than on defects, and every such report has to be answered with an entry in
    // the exclude filter that says nothing about the code under test.
    enabled = name == "spotbugsMain"
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
