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

tasks.register("createMacApp") {
    dependsOn(tasks.shadowJar, tasks.named("generateAppIcon"))
    group = "distribution"
    description = "Creates a minimal macOS .app bundle to fix the application name issue"

    doLast {
        val appName = "SMSGW Client"
        val exeName = "SMSGWClient"
        val bundleDir = layout.buildDirectory.dir("$appName.app").get().asFile
        
        if (bundleDir.exists()) {
            bundleDir.deleteRecursively()
        }

        val contentsDir = File(bundleDir, "Contents")
        val macOSDir = File(contentsDir, "MacOS")
        val resourcesDir = File(contentsDir, "Resources")

        macOSDir.mkdirs()
        resourcesDir.mkdirs()

        // 1. Copy JAR
        val jarFile = tasks.shadowJar.get().archiveFile.get().asFile
        val targetJar = File(resourcesDir, jarFile.name)
        jarFile.copyTo(targetJar, overwrite = true)

        // 2. Copy Generated Icon
        val generatedIcon = layout.buildDirectory.file("appIcon.png").get().asFile
        if (generatedIcon.exists()) {
            generatedIcon.copyTo(File(resourcesDir, "appIcon.png"), overwrite = true)
        }

        // 3. Create Launcher Script with improved path finding and full logging
        val launcherScript = File(macOSDir, exeName)
        val dollar = "$"
        launcherScript.writeText("""
            #!/bin/bash
            # Full debug logging to /tmp/smsgw_launcher.log
            exec > "/tmp/smsgw_launcher.log" 2>&1
            set -x
            
            echo "Launcher started at ${dollar}(date)"
            echo "User: ${dollar}USER"
            echo "Working directory: ${dollar}(pwd)"

            # Expand PATH to include common locations and Homebrew openjdk
            export PATH="/opt/homebrew/opt/openjdk/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:${dollar}PATH"
            echo "Path: ${dollar}PATH"

            DIR="${dollar}(cd "${dollar}(dirname "${dollar}0")" && pwd)"
            echo "Script directory: ${dollar}DIR"

            JAR_PATH="${dollar}DIR/../Resources/${jarFile.name}"
            echo "Looking for JAR at: ${dollar}JAR_PATH"
            
            # 1. Try Homebrew openjdk directly (common on Apple Silicon)
            if [ -x "/opt/homebrew/opt/openjdk/bin/java" ]; then
                JAVA_CMD="/opt/homebrew/opt/openjdk/bin/java"
                echo "Found Homebrew openjdk java: ${dollar}JAVA_CMD"
            fi

            # 2. Try /usr/libexec/java_home
            if [ -z "${dollar}JAVA_CMD" ] && [ -x "/usr/libexec/java_home" ]; then
                export JAVA_HOME=${dollar}(/usr/libexec/java_home 2>/dev/null)
                if [ -n "${dollar}JAVA_HOME" ]; then
                    JAVA_CMD="${dollar}JAVA_HOME/bin/java"
                    echo "JAVA_HOME from java_home: ${dollar}JAVA_HOME"
                fi
            fi
            
            # 3. Search for any JDK in /Library/Java/JavaVirtualMachines if still not found
            if [ -z "${dollar}JAVA_CMD" ]; then
                JDK_PATH=${dollar}(ls -d /Library/Java/JavaVirtualMachines/*/Contents/Home 2>/dev/null | tail -n 1)
                if [ -n "${dollar}JDK_PATH" ]; then
                    export JAVA_HOME="${dollar}JDK_PATH"
                    JAVA_CMD="${dollar}JAVA_HOME/bin/java"
                    echo "Found JDK manually: ${dollar}JAVA_HOME"
                fi
            fi

            # 4. Final fallback to java in PATH
            if [ -z "${dollar}JAVA_CMD" ]; then
                # Avoid using /usr/bin/java if possible as it's often a stub
                JAVA_CMD=${dollar}(which java)
                echo "Falling back to 'which java': ${dollar}JAVA_CMD"
            fi

            if [ ! -x "${dollar}JAVA_CMD" ]; then
                osascript -e "display dialog \"Java Runtime not found. Please install Java (JDK).\" buttons {\"OK\"} default button \"OK\" with icon stop"
                exit 1
            fi
            
            echo "Final JAVA_CMD: ${dollar}JAVA_CMD"
            "${dollar}JAVA_CMD" -version

            echo "Executing: ${dollar}JAVA_CMD -jar ${dollar}JAR_PATH"
            cd "${dollar}DIR/../Resources"
            
            exec "${dollar}JAVA_CMD" \
                -Xdock:name="$appName" \
                -Dapple.awt.application.name="$appName" \
                -Dcom.apple.mrj.application.apple.menu.about.name="$appName" \
                --enable-native-access=ALL-UNNAMED \
                -jar "${jarFile.name}"
        """.trimIndent().trim())
        launcherScript.setExecutable(true)

        // 4. Create Info.plist with Icon reference
        val plistFile = File(contentsDir, "Info.plist")
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
                <string>$exeName</string>
                <key>CFBundleIconFile</key>
                <string>appIcon.png</string>
                <key>NSHighResolutionCapable</key>
                <true/>
                <key>LSMinimumSystemVersion</key>
                <string>10.13</string>
            </dict>
            </plist>
        """.trimIndent().trim())
        
        logger.lifecycle("SUCCESS: macOS App Bundle updated at: ${bundleDir.absolutePath}")
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
