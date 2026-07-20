plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.2.10"
    application
}

application {
    mainClass.set("hu.infokristaly.bluetoothsmsgateway.client.MainKt")
}

tasks.withType<JavaExec> {
    standardInput = System.`in`
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
}
