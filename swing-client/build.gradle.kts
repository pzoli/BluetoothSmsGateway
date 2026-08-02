import java.util.concurrent.TimeUnit

plugins {
    kotlin("jvm")
    application
    alias(libs.plugins.shadow)
}

application {
    mainClass.set("hu.infokristaly.bluetoothsmsgateway.swing.LauncherKt")
    applicationDefaultJvmArgs = listOf(
        "-Xdock:name=SMSGW Client",
        "-Dapple.awt.application.name=SMSGW Client",
        "-Dcom.apple.mrj.application.apple.menu.about.name=SMSGW Client"
    )
}

tasks.withType<JavaExec> {
    jvmArgs = listOf(
        "-Xdock:name=SMSGW Client",
        "-Dapple.awt.application.name=SMSGW Client",
        "-Dcom.apple.mrj.application.apple.menu.about.name=SMSGW Client",
        "--enable-native-access=ALL-UNNAMED"
    )
}

tasks.register<JavaExec>("generateAppIcon") {
    dependsOn(tasks.classes)
    mainClass.set("hu.infokristaly.bluetoothsmsgateway.swing.LauncherKt")
    classpath = sourceSets.main.get().runtimeClasspath
    val iconPath = layout.buildDirectory.file("appIcon.png").get().asFile.absolutePath
    args = listOf("--save-icon", iconPath)
}

tasks.register("packageMacDmg") {
    dependsOn(tasks.shadowJar, tasks.named("generateAppIcon"))
    group = "distribution"
    description = "Packages the application as a macOS DMG using jpackage"

    doLast {
        val appName = "SMSGW Client"
        val buildDir = layout.buildDirectory.get().asFile
        val inputDir = File(buildDir, "jpackage-input")
        val outputDir = File(buildDir, "dist")
        val pngIcon = layout.buildDirectory.file("appIcon.png").get().asFile
        val icnsIcon = File(buildDir, "appIcon.icns")
        
        if (inputDir.exists()) inputDir.deleteRecursively()
        if (outputDir.exists()) outputDir.deleteRecursively()
        inputDir.mkdirs()
        outputDir.mkdirs()

        // 1. Copy the main JAR to input directory
        val jarFile = tasks.shadowJar.get().archiveFile.get().asFile
        jarFile.copyTo(File(inputDir, jarFile.name))

        // 2. Convert PNG to ICNS (macOS specific)
        println("Converting icon to .icns format...")
        val iconsetDir = File(buildDir, "appIcon.iconset")
        if (iconsetDir.exists()) iconsetDir.deleteRecursively()
        iconsetDir.mkdirs()
        
        // Generate different sizes for the iconset
        val sizes = listOf(16, 32, 64, 128, 256, 512)
        sizes.forEach { size ->
            val iconName = if (size <= 512) "icon_${size}x${size}.png" else "icon_${size/2}x${size/2}@2x.png"
            ProcessBuilder("sips", "-z", size.toString(), size.toString(), pngIcon.absolutePath, "--out", File(iconsetDir, iconName).absolutePath)
                .start().waitFor()
        }
        
        // Create the final .icns file
        ProcessBuilder("iconutil", "-c", "icns", iconsetDir.absolutePath, "-o", icnsIcon.absolutePath)
            .start().waitFor()

        // 3. Find jpackage
        var jpackageCmd = "jpackage"
        val homebrewJpackage = File("/opt/homebrew/opt/openjdk/bin/jpackage")
        if (homebrewJpackage.exists()) {
            jpackageCmd = homebrewJpackage.absolutePath
        }

        // 4. Run jpackage
        println("Running jpackage with custom icon...")
        val process = ProcessBuilder(
            jpackageCmd,
            "--type", "dmg",
            "--dest", outputDir.absolutePath,
            "--name", appName,
            "--input", inputDir.absolutePath,
            "--main-jar", jarFile.name,
            "--main-class", "hu.infokristaly.bluetoothsmsgateway.swing.LauncherKt",
            "--icon", icnsIcon.absolutePath,
            "--app-version", "1.0.0",
            "--vendor", "InfoKristaly",
            "--mac-package-identifier", "hu.infokristaly.bluetoothsmsgateway.swing",
            "--java-options", "-Xdock:name=\"$appName\"",
            "--java-options", "-Dapple.awt.application.name=\"$appName\"",
            "--java-options", "--enable-native-access=ALL-UNNAMED"
        ).inheritIO().start()
        
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("jpackage failed with exit code $exitCode")
        }

        iconsetDir.deleteRecursively()
        println("SUCCESS: macOS DMG created in: ${outputDir.absolutePath}")
    }
}

// Fixed for shadow plugin compatibility with modern Gradle
tasks.shadowJar {
    archiveFileName.set("swing-client.jar")
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
    implementation("com.formdev:flatlaf:3.7.2")
    implementation("com.formdev:flatlaf-intellij-themes:3.7.2")

    // QR Code generation
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")
}
