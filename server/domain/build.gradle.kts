plugins {
    id("gal.conxugal.java-conventions")
    alias(libs.plugins.micronaut.library)
}

dependencies {
    implementation(project(":commons"))
    api(libs.micronaut.data.model)
    implementation(libs.micronaut.data.tx)
    implementation(libs.jspecify)
    implementation(libs.slf4j.api)
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
