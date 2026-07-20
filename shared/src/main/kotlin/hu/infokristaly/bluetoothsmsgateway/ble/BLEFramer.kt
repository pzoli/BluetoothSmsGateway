package hu.infokristaly.bluetoothsmsgateway.ble

class BLEFramer {
    private val buffer = StringBuilder()

    fun append(
        data: ByteArray
    ): List<String> {
        buffer.append(data.decodeToString())
        val messages = mutableListOf<String>()
        while (true) {
            val index = buffer.indexOf("\n")
            if (index == -1) break
            messages.add(buffer.substring(0, index))
            buffer.delete(0, index + 1)
        }
        return messages
    }
}
