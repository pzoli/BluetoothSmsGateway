package hu.infokristaly.bluetoothsmsgateway.client

import hu.infokristaly.bluetoothsmsgateway.BleProtocol
import hu.infokristaly.bluetoothsmsgateway.ble.*
import kotlinx.coroutines.runBlocking
import java.util.*

fun main(args: Array<String>) {
    println("Bluetooth SMS Gateway JavaSE Client")
    println("Service UUID: ${BleProtocol.SERVICE_UUID}")
    
    when {
        args.contains("--mock") -> runMockClient()
        args.contains("--ble") -> runBleClient()
        else -> {
            println("\nOptions:")
            println("  --mock : Run in mock mode (simulation)")
            println("  --ble  : Run using real Bluetooth (requires SimpleJavaBLE native libs)")
        }
    }
}

fun runBleClient() {
    val client = KableBleClient()
    
    println("Starting BLE Client...")
    client.start { event ->
        println("\n[EVENT] ${event.action}")
        if (event.action == "sms_received") {
            val payload = BLECodec.json.decodeFromJsonElement(SmsReceivedPayload.serializer(), event.payload!!)
            println("SMS from ${payload.from}: ${payload.text}")
        }
    }

    val scanner = Scanner(System.`in`)
    println("\nEnter 'send <phone> <text>' to send SMS, or 'quit' to exit:")
    
    while (true) {
        print("> ")
        val line = scanner.nextLine()
        if (line == "quit") break
        
        if (line.startsWith("send ")) {
            val parts = line.split(" ", limit = 3)
            if (parts.size == 3) {
                val phone = parts[1]
                val text = parts[2]
                val request = BLEProtocol.sendSms(System.currentTimeMillis(), phone, text)
                client.sendCommand(request)
                println("Request sent.")
            } else {
                println("Usage: send <phone> <text>")
            }
        }
    }
    
    client.stop()
}

fun runMockClient() = runBlocking {
    val framer = BLEFramer()
    
    // Simulate receiving an SMS event from the server
    val incomingSms = BLEProtocol.smsReceived("+123456789", "Hello from Android!")
    val packets = BLECodec.encodeToByteArrayList(incomingSms)
    
    println("\n[Mock] Simulating incoming SMS event...")
    packets.forEach { packet ->
        val messages = framer.append(packet)
        messages.forEach { msgJson ->
            val message = BLECodec.decode(msgJson)
            println("Received Event: ${message.action}")
            if (message.action == "sms_received") {
                val payload = BLECodec.json.decodeFromJsonElement(SmsReceivedPayload.serializer(), message.payload!!)
                println("SMS from ${payload.from}: ${payload.text}")
            }
        }
    }
    
    // Simulate sending an SMS request to the server
    println("\n[Mock] Simulating outgoing SMS request...")
    val sendRequest = BLEProtocol.sendSms(System.currentTimeMillis(), "+987654321", "Hello from Java!")
    val requestBytes = BLECodec.encode(sendRequest)
    println("Encoded Request (to be sent over BLE): ${String(requestBytes).trim()}")
}
