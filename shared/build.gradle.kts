plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    `java-library`
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}
