import java.util.concurrent.TimeUnit

plugins {
    kotlin("jvm")
    application
    alias(libs.plugins.shadow)
}

val isMac = System.getProperty("os.name").lowercase().contains("mac")

application {
    mainClass.set("hu.infokristaly.bluetoothsmsgateway.swing.LauncherKt")
    applicationDefaultJvmArgs = buildList {
        if (isMac) {
            add("-Xdock:name=SMSGW Client")
            add("-Dapple.awt.application.name=SMSGW Client")
            add("-Dcom.apple.mrj.application.apple.menu.about.name=SMSGW Client")
        }
    }
}

tasks.withType<JavaExec> {
    jvmArgs = buildList {
        if (isMac) {
            add("-Xdock:name=SMSGW Client")
            add("-Dapple.awt.application.name=SMSGW Client")
            add("-Dcom.apple.mrj.application.apple.menu.about.name=SMSGW Client")
        }
        add("--enable-native-access=ALL-UNNAMED")
    }
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
        val resourceDir = File(buildDir, "jpackage-resources")
        val pngIcon = layout.buildDirectory.file("appIcon.png").get().asFile
        val icnsIcon = File(buildDir, "$appName.icns") // Match app name exactly
        
        if (inputDir.exists()) inputDir.deleteRecursively()
        if (outputDir.exists()) outputDir.deleteRecursively()
        if (resourceDir.exists()) resourceDir.deleteRecursively()
        
        inputDir.mkdirs()
        outputDir.mkdirs()
        resourceDir.mkdirs()

        // 1. Copy the main JAR to input directory
        val jarFile = tasks.shadowJar.get().archiveFile.get().asFile
        jarFile.copyTo(File(inputDir, jarFile.name))

        // 2. Convert PNG to ICNS (Exact naming for iconutil)
        println("Generating professional macOS .icns format...")
        val iconsetDir = File(buildDir, "appIcon.iconset")
        if (iconsetDir.exists()) iconsetDir.deleteRecursively()
        iconsetDir.mkdirs()
        
        // Exact naming required by iconutil
        val iconSizes = mapOf(
            "icon_16x16.png" to 16,
            "icon_16x16@2x.png" to 32,
            "icon_32x32.png" to 32,
            "icon_32x32@2x.png" to 64,
            "icon_128x128.png" to 128,
            "icon_128x128@2x.png" to 256,
            "icon_256x256.png" to 256,
            "icon_256x256@2x.png" to 512,
            "icon_512x512.png" to 512,
            "icon_512x512@2x.png" to 1024
        )

        iconSizes.forEach { (fileName, size) ->
            ProcessBuilder("sips", "-z", size.toString(), size.toString(), pngIcon.absolutePath, "--out", File(iconsetDir, fileName).absolutePath)
                .start().waitFor()
        }
        
        ProcessBuilder("iconutil", "-c", "icns", iconsetDir.absolutePath, "-o", icnsIcon.absolutePath)
            .start().waitFor()

        // 3. Create Info.plist override for Bluetooth permissions
        val plistFile = File(resourceDir, "Info.plist")
        plistFile.writeText("""
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>CFBundleName</key>
                <string>$appName</string>
                <key>CFBundleDisplayName</key>
                <string>$appName</string>
                <key>CFBundleIdentifier</key>
                <string>hu.infokristaly.bluetoothsmsgateway.swing</string>
                <key>CFBundleVersion</key>
                <string>1.0</string>
                <key>CFBundleShortVersionString</key>
                <string>1.0</string>
                <key>CFBundlePackageType</key>
                <string>APPL</string>
                <key>CFBundleSignature</key>
                <string>????</string>
                <key>CFBundleExecutable</key>
                <string>$appName</string>
                <key>CFBundleIconFile</key>
                <string>$appName</string>
                <key>NSHighResolutionCapable</key>
                <true/>
                <key>LSMinimumSystemVersion</key>
                <string>10.13</string>
                <key>NSBluetoothAlwaysUsageDescription</key>
                <string>SMSGW Client needs Bluetooth access to communicate with your Android phone for SMS and Calls.</string>
                <key>NSBluetoothPeripheralUsageDescription</key>
                <string>SMSGW Client needs Bluetooth access to communicate with your Android phone for SMS and Calls.</string>
            </dict>
            </plist>
        """.trimIndent().trim())

        // 4. Find jpackage
        var jpackageCmd = "jpackage"
        val homebrewJpackage = File("/opt/homebrew/opt/openjdk/bin/jpackage")
        if (homebrewJpackage.exists()) {
            jpackageCmd = homebrewJpackage.absolutePath
        }

        // 5. Run jpackage
        println("Running jpackage with optimized settings...")
        val process = ProcessBuilder(
            jpackageCmd,
            "--type", "dmg",
            "--dest", outputDir.absolutePath,
            "--name", appName,
            "--input", inputDir.absolutePath,
            "--main-jar", jarFile.name,
            "--main-class", "hu.infokristaly.bluetoothsmsgateway.swing.LauncherKt",
            "--icon", icnsIcon.absolutePath,
            "--resource-dir", resourceDir.absolutePath,
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
