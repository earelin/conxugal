plugins {
    id("gal.conxugal.java-conventions")
    alias(libs.plugins.micronaut.library)
}

dependencies {
    implementation(libs.micronaut.data.model)
    implementation(libs.jspecify)
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
