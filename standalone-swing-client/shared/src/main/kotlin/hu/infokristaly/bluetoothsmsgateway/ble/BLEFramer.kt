package hu.infokristaly.bluetoothsmsgateway.ble

import java.io.ByteArrayOutputStream

class BLEFramer(
    private val maxBufferSize: Int = 16 * 1024 * 1024
) {
    private val buffer = ByteArrayOutputStream()

    @Synchronized
    fun append(
        data: ByteArray
    ): List<String> {
        if (buffer.size() + data.size > maxBufferSize) {
            System.err.println("BLEFramer: Buffer size limit ($maxBufferSize bytes) exceeded. Resetting buffer.")
            buffer.reset()
        }
        buffer.write(data)
        val bytes = buffer.toByteArray()
        val messages = mutableListOf<String>()
        var start = 0

        for (i in bytes.indices) {
            if (bytes[i] == '\n'.code.toByte()) {
                val lineBytes = bytes.copyOfRange(start, i)
                val lineStr = lineBytes.toString(Charsets.UTF_8).trimEnd('\r')
                if (lineStr.isNotEmpty()) {
                    messages.add(lineStr)
                }
                start = i + 1
            }
        }

        buffer.reset()
        if (start < bytes.size) {
            buffer.write(bytes, start, bytes.size - start)
        }

        return messages
    }

    @Synchronized
    fun reset() {
        buffer.reset()
    }
}
