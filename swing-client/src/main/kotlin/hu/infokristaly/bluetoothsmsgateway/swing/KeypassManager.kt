package hu.infokristaly.bluetoothsmsgateway.swing

import java.security.SecureRandom


object KeypassManager {
    
    var currentKeypass: String = ""
        private set

    init {
        generateNewKeypass()
    }

    fun generateNewKeypass(): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val random = SecureRandom()
        currentKeypass = (1..40)
            .map { allowedChars[random.nextInt(allowedChars.size)] }
            .joinToString("")
        
        return currentKeypass
    }
}
