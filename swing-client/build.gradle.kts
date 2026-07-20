plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("hu.infokristaly.bluetoothsmsgateway.swing.SwingMainKt")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    implementation(project(":client-core"))
    implementation(libs.kotlinx.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
}
