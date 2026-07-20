package hu.infokristaly.bluetoothsmsgateway.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolTest {

    @Test
    fun testEncodeDecode() {
        val original = BLEProtocol.sendSms(123L, "+123456", "Test msg")
        val encoded = BLECodec.encode(original)
        
        val framer = BLEFramer()
        val messages = framer.append(encoded)
        
        assertEquals(1, messages.size)
        val decoded = BLECodec.decode(messages[0])
        
        assertEquals(original.id, decoded.id)
        assertEquals(original.action, decoded.action)
    }

    @Test
    fun testFragmentation() {
        val longText = "A".repeat(500)
        val original = BLEProtocol.sendSms(124L, "+123456", longText)
        val packets = BLECodec.encodeToByteArrayList(original)
        
        // MTU is 180 by default in BLECodec.split
        // JSON of this message will be > 500 chars, so it should be at least 3 packets
        assert(packets.size >= 3)
        
        val framer = BLEFramer()
        var decodedMessages = emptyList<String>()
        packets.forEach { 
            decodedMessages += framer.append(it)
        }
        
        assertEquals(1, decodedMessages.size)
        val decoded = BLECodec.decode(decodedMessages[0])
        assertEquals(original.id, decoded.id)
    }
}
