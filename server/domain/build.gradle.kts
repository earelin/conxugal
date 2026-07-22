plugins {
    id("gal.conxugal.java-conventions")
    alias(libs.plugins.micronaut.library)
    alias(libs.plugins.pitest)
}

dependencies {
    implementation(project(":commons"))
    implementation(libs.micronaut.data.model)
    implementation(libs.jspecify)
    implementation(libs.slf4j.api)
    implementation(libs.jakarta.transaction.api)
    annotationProcessor(libs.micronaut.data.processor)

    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.junit.jupiter)
}

micronaut {
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("gal.conxugal.domain.*")
    }
}

pitest {
    targetClasses = listOf("gal.conxugal.domain.*")
    excludedClasses = listOf("*\$Introspection*", "*\$Definition*", "*\$BeanDefinition*")
    junit5PluginVersion = libs.versions.pitestJunit5Plugin.get()
    threads = 4
}
