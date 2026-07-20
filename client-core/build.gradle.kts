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
    implementation("com.juul.kable:kable-core:0.40.0")
    implementation("com.juul.kable:kable-btleplug-ffi:0.40.0")
}
