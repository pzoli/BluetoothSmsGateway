plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.2.10"
    application
    alias(libs.plugins.shadow)
}

application {
    mainClass.set("hu.infokristaly.bluetoothsmsgateway.client.MainKt")
}

tasks.shadowJar {
    archiveFileName.set("javase-client.jar")
    mergeServiceFiles()
}

tasks.distZip {
    dependsOn(tasks.shadowJar)
}

tasks.distTar {
    dependsOn(tasks.shadowJar)
}

tasks.startScripts {
    dependsOn(tasks.shadowJar)
}

// Disabling standard jar task to avoid conflicts with shadow
tasks.jar {
    enabled = false
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
