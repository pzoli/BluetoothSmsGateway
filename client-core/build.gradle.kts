plugins {
    kotlin("jvm")
    `java-library`
    kotlin("plugin.serialization") version "2.2.10"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    api(project(":shared"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    
    // Kable core and FFI backend
    implementation(libs.kable.core)
    implementation(libs.kable.btleplug)
}
