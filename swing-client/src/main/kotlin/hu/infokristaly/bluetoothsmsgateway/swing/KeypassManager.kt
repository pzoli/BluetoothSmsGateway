package hu.infokristaly.bluetoothsmsgateway.swing

import java.security.SecureRandom
import java.util.prefs.Preferences

object KeypassManager {
    private const val KEY_KEYPASS = "ble_keypass"
    private val prefs = Preferences.userNodeForPackage(KeypassManager::class.java)
    
    var currentKeypass: String = ""
        private set

    init {
        currentKeypass = prefs.get(KEY_KEYPASS, "")
        if (currentKeypass.isBlank()) {
            generateNewKeypass()
        }
    }

    fun generateNewKeypass(): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val random = SecureRandom()
        currentKeypass = (1..40)
            .map { allowedChars[random.nextInt(allowedChars.size)] }
            .joinToString("")
        
        prefs.put(KEY_KEYPASS, currentKeypass)
        return currentKeypass
    }
}
