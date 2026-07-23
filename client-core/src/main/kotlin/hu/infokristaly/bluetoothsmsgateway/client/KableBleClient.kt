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
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        log("CRITICAL BLE ERROR: ${throwable.message}")
        isRunning.set(false)
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exceptionHandler)
    private var onLogCallback: (String) -> Unit = {}
    var keypass: String? = null

    private fun log(message: String) {
        println(message)
        onLogCallback(message)
    }

    private var activeJob: Job? = null

    fun start(
        onStatusChange: (String) -> Unit = {},
        onLog: (String) -> Unit = {},
        onEvent: (BLEMessage) -> Unit
    ) {
        this.onLogCallback = onLog
        activeJob?.cancel()
        framer.reset()

        activeJob = scope.launch {
            try {
                if (peripheral != null) {
                    log("Cleaning up old peripheral connection...")
                    try {
                        peripheral!!.disconnect()
                    } catch (_: Exception) {}
                    peripheral = null
                    delay(500.milliseconds) 
                }
                
                // Now we are ready for a real connection attempt
                val scanner = Scanner()
                log("Scanning for $deviceName...")
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
                val p = Peripheral(advertisement)
                peripheral = p

                // Start monitoring state in a separate coroutine that lives as long as the client is active
                scope.launch {
                    log("DEBUG: State observer flow started for ${advertisement.identifier}")
                    p.state.collect { state ->
                        log("Connection state change: $state")
                        val statusString = when (state) {
                            is State.Connecting -> "Connecting"
                            is State.Connected -> "Connected"
                            is State.Disconnecting -> "Disconnecting"
                            is State.Disconnected -> "Disconnected"
                        }
                        onStatusChange(statusString)
                        
                        if (state is State.Disconnected) {
                            isRunning.set(false)
                            log("Detected Disconnected state. BLE communication marked as stopped.")
                        }
                    }
                    log("DEBUG: State observer flow terminated")
                }

                log("Connecting to $deviceName (timeout 15s)...")
                onStatusChange("Connecting")
                
                withTimeout(15.seconds) {
                    p.connect()
                }
                
                log("Successfully connected to $deviceName")
                log("NOTE: Encryption is enabled. If this is the first connection, look for a Pairing Request on your devices.")
                onStatusChange("Connected")
                
                isRunning.set(true)

                val eventChar = characteristicOf(
                    service = BleProtocol.SERVICE_UUID,
                    characteristic = BleProtocol.EVENT_UUID
                )

                log("Subscribing to notifications for ${BleProtocol.EVENT_UUID}...")
                
                launch {
                    try {
                        p.observe(eventChar)
                            .collect { data ->
                                log("DEBUG: Received raw packet (${data.size} bytes)")
                                val messages = framer.append(data)
                                messages.forEach { msgJson ->
                                    try {
                                        val message = BLECodec.decode(msgJson)
                                        if (message.action == "server_stopping") {
                                            log("SERVER SIGNAL: Server is stopping. Disconnecting...")
                                            isRunning.set(false)
                                            peripheral?.disconnect()
                                        } else {
                                            onEvent(message)
                                        }
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

            } catch (e: TimeoutCancellationException) {
                log("Connection timed out. Check if phone is in range.")
                onStatusChange("Timeout")
                isRunning.set(false)
            } catch (e: CancellationException) {
                log("BLE Client operation cancelled")
                isRunning.set(false)
            } catch (e: Exception) {
                log("BLE Client Error: ${e.message}")
                onStatusChange("Error")
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
        activeJob?.cancel()
        activeJob = null
        framer.reset()
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
        activeJob?.cancel()
        activeJob = null
        framer.reset()
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
