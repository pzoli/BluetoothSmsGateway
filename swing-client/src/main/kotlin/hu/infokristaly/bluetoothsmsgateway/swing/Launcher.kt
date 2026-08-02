package hu.infokristaly.bluetoothsmsgateway.swing

/**
 * Entry point for the application that ensures system properties are set BEFORE
 * any AWT/Swing classes are loaded. This is critical for macOS to correctly
 * display the application name in the Dock and Menu Bar.
 */
fun main(args: Array<String>) {
    // Handle icon generation request from build script
    if (args.contains("--save-icon")) {
        val index = args.indexOf("--save-icon")
        if (index + 1 < args.size) {
            val file = java.io.File(args[index + 1])
            AppIcon.saveToPng(file, 512)
            return
        }
    }

    // Set the application name for macOS and other platforms
    val appName = "SMSGW Client"
    System.setProperty("apple.awt.application.name", appName)
    System.setProperty("com.apple.mrj.application.apple.menu.about.name", appName)
    
    // Additional macOS-specific tweaks
    System.setProperty("apple.laf.useScreenMenuBar", "true")
    System.setProperty("apple.awt.application.appearance", "system")
    
    // Also set some generic Swing properties that should be set early
    System.setProperty("awt.useSystemAAFontSettings", "on")
    System.setProperty("swing.aatext", "true")

    // Now start the actual Swing application
    SwingMain.start(args)
}
