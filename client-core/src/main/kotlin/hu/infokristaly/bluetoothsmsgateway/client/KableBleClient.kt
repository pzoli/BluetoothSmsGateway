package hu.infokristaly.bluetoothsmsgateway.client

import com.juul.kable.*
import hu.infokristaly.bluetoothsmsgateway.BleProtocol
import hu.infokristaly.bluetoothsmsgateway.ble.BLECodec
import hu.infokristaly.bluetoothsmsgateway.ble.BLEFramer
import hu.infokristaly.bluetoothsmsgateway.ble.BLEMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class KableBleClient(
    private val deviceName: String = "SMSGW",
) {
    private var peripheral: Peripheral? = null
    private val framer = BLEFramer()
    private val isRunning = AtomicBoolean(false)
    private val bleDispatcher = newSingleThreadContext("BLE")
    private val scope = CoroutineScope(bleDispatcher + SupervisorJob())
    private var onLogCallback: (String) -> Unit = {}
    var keypass: String? = null

    private fun log(message: String) {
        println(message)
        onLogCallback(message)
    }

    fun start(
        onStatusChange: (String) -> Unit = {},
        onLog: (String) -> Unit = {},
        onEvent: (BLEMessage) -> Unit
    ) {
        this.onLogCallback = onLog
        scope.launch {
            try {
                if (peripheral != null) {
                    try {
                        peripheral!!.disconnect()
                    } catch (_: Exception) {}
                    peripheral = null
                }
                
                // No filters for maximum visibility during debugging
                val scanner = Scanner()

                log("Scanning for everything (Looking for $deviceName)...")
                onStatusChange("Scanning")
                
                val advertisement = withTimeoutOrNull(20.seconds) {
                    scanner.advertisements
                        .onEach { 
                            val name = it.name ?: "Unknown"
                            // Only log if it's likely our device or to show progress
                            if (name.contains("SMS") || name == "Unknown") {
                                log("DEBUG: Found candidate: $name [${it.identifier}]")
                            }
                        }
                        .firstOrNull { (it.name == deviceName) || (it.name?.contains(deviceName) == true) }
                }

                if (advertisement == null) {
                    log("Device $deviceName not found within 20s. Check if phone is Advertising and Bluetooth is ON.")
                    onStatusChange("Not Found")
                    return@launch
                }

                log("Found device: ${advertisement.name} [${advertisement.identifier}]")
                peripheral = Peripheral(advertisement)

                scope.launch {
                    peripheral!!.state.collect { state ->
                        log("Connection state: $state")
                        if (state is State.Disconnected) {
                            isRunning.set(false)
                        }
                    }
                }

                log("Connecting to $deviceName...")
                onStatusChange("Connecting")
                peripheral!!.connect()
                log("Connected to $deviceName")
                log("NOTE: Encryption is enabled. If this is the first connection, look for a Pairing Request on your devices.")
                onStatusChange("Connected")
                
                isRunning.set(true)

                val eventChar = characteristicOf(
                    service = BleProtocol.SERVICE_UUID,
                    characteristic = BleProtocol.EVENT_UUID
                )

                log("Subscribing to notifications for ${BleProtocol.EVENT_UUID}...")
                
                scope.launch {
                    try {
                        peripheral!!.observe(eventChar)
                            .collect { data ->
                                log("DEBUG: Received raw packet (${data.size} bytes)")
                                val messages = framer.append(data)
                                messages.forEach { msgJson ->
                                    try {
                                        val message = BLECodec.decode(msgJson)
                                        onEvent(message)
                                    } catch (e: Exception) {
                                        log("Error decoding event: ${e.message}")
                                        log("Raw JSON: $msgJson")
                                    }
                                }
                            }
                    } catch (e: Exception) {
                        log("Observation stream error: ${e.message}")
                    }
                }

                log("Client is now ready for commands.")

            } catch (e: Exception) {
                log("BLE Client Error: ${e.message}")
                isRunning.set(false)
            }
        }
    }

    fun sendCommand(message: BLEMessage) {
        val p = peripheral ?: run {
            log("Error sending command: Peripheral is null")
            return
        }
        
        if (!isRunning.get()) {
            log("Error sending command: Client is not running")
            return
        }

        scope.launch {
            try {
                val commandChar = characteristicOf(
                    service = BleProtocol.SERVICE_UUID,
                    characteristic = BleProtocol.COMMAND_UUID
                )
                
                val messageWithKey = message.copy(keypass = keypass)
                val packets = BLECodec.encodeToByteArrayList(messageWithKey)
                log("Sending ${packets.size} packets...")
                packets.forEachIndexed { index, packet ->
                    var success = false
                    var attempt = 1
                    val maxAttempts = 3
                    
                    while (!success && attempt <= maxAttempts) {
                        try {
                            if (p.state.value !is State.Connected) {
                                throw IllegalStateException("Lost connection")
                            }
                            
                            withTimeout(5.seconds) {
                                p.write(commandChar, packet, WriteType.WithoutResponse)
                            }
                            success = true
                        } catch (e: Exception) {
                            log("Packet ${index + 1} attempt $attempt failed: ${e.message}")
                            if (attempt < maxAttempts) {
                                delay(1.seconds)
                                attempt++
                            } else {
                                throw e
                            }
                        }
                    }
                    
                    if (index < packets.size - 1) {
                        delay(100.milliseconds)
                    }
                }
                log("Command sent successfully (${packets.size} packets)")
            } catch (e: Exception) {
                log("Error sending command: ${e.message}")
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        val p = peripheral
        peripheral = null
        scope.launch {
            try {
                p?.disconnect()
                log("Disconnected from $deviceName")
            } catch (e: Exception) {
                log("Error during disconnect: ${e.message}")
            }
        }
    }

    suspend fun disconnectSync() {
        isRunning.set(false)
        val p = peripheral
        peripheral = null
        try {
            p?.disconnect()
            log("Synchronously disconnected from $deviceName")
        } catch (e: Exception) {
            log("Error during synchronous disconnect: ${e.message}")
        }
    }
}
