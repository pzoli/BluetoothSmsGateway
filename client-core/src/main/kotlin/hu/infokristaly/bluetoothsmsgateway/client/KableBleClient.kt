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
    private var commandChar: Characteristic? = null
    private var eventChar: Characteristic? = null
    private val framer = BLEFramer()
    private val isRunning = AtomicBoolean(false)
    private val bleDispatcher = newSingleThreadContext("BLE")
    private val scope = CoroutineScope(bleDispatcher + SupervisorJob())

    fun start(
        onStatusChange: (String) -> Unit = {},
        onEvent: (BLEMessage) -> Unit
    ) {
        scope.launch {
            try {
                // Cleanup previous connection if any
                if (peripheral != null) {
                    try {
                        peripheral!!.disconnect()
                    } catch (_: Exception) {}
                    peripheral = null
                }
                
                val scanner = Scanner {
                    filters {
                        match {
                            services = listOf(BleProtocol.SERVICE_UUID)
                        }
                    }
                }

                println("Scanning for $deviceName...")
                onStatusChange("Scanning")
                val advertisement = scanner.advertisements
                    .firstOrNull { (it.name == deviceName) || (it.name?.contains(deviceName) == true) }

                if (advertisement == null) {
                    println("Device $deviceName not found.")
                    onStatusChange("Not Found")
                    return@launch
                }

                println("Found device: ${advertisement.name} [${advertisement.identifier}]")
                peripheral = Peripheral(advertisement)

                // Observe connection state
                scope.launch {
                    peripheral!!.state.collect { state ->
                        println("Connection state: $state")
                        if (state is State.Disconnected) {
                            isRunning.set(false)
                        }
                    }
                }

                println("Connecting to $deviceName...")
                onStatusChange("Connecting")
                peripheral!!.connect()
                println("Connected to $deviceName")
                onStatusChange("Connected")
                
                // Wait for services to be discovered and populated
                println("Waiting for services to be discovered...")
                val discoveredServices = peripheral!!.services.filterNotNull().first { it.isNotEmpty() }
                println("Services discovered.")

                // Eagerly resolve characteristics to ensure D-Bus handles are ready
                val service = discoveredServices.find { it.serviceUuid == BleProtocol.SERVICE_UUID }
                    ?: throw IllegalStateException("Service ${BleProtocol.SERVICE_UUID} not found")
                
                commandChar = service.characteristics.find { it.characteristicUuid == BleProtocol.COMMAND_UUID }
                    ?: throw IllegalStateException("Command characteristic not found")
                
                eventChar = service.characteristics.find { it.characteristicUuid == BleProtocol.EVENT_UUID }
                    ?: throw IllegalStateException("Event characteristic not found")

                println("Characteristics resolved and cached.")
                
                // Give more time for GATT table to stabilize on Linux
                println("Settling GATT table (3s delay)...")
                delay(3.seconds)

                // Observe notifications
                println("Subscribing to notifications for ${BleProtocol.EVENT_UUID}...")
                
                try {
                    // Start observing in a separate job
                    scope.launch {
                        peripheral!!.observe(eventChar!!)
                            .collect { data ->
                                val messages = framer.append(data)
                                messages.forEach { msgJson ->
                                    try {
                                        val message = BLECodec.decode(msgJson)
                                        onEvent(message)
                                    } catch (e: Exception) {
                                        println("Error decoding event: ${e.message}")
                                        println("Raw JSON: $msgJson")
                                    }
                                }
                            }
                    }

                    // Wait for the CCCD write (notification enablement) to settle
                    println("Settling subscription (3s delay)...")
                    delay(3.seconds)
                    
                    // Warm up the session with a read to ensure BlueZ GATT handles are active
                    println("Warming up session...")
                    try {
                        withTimeout(2.seconds) {
                            peripheral!!.read(commandChar!!)
                        }
                        println("Session warmed up and ready.")
                    } catch (e: Exception) {
                        if (e is TimeoutCancellationException) {
                            println("Warm-up read TIMED OUT. Linux stack might be unstable.")
                        } else {
                            println("Warm-up read failed: ${e.message}")
                        }
                    }

                    isRunning.set(true)
                    println("Client is now ready for commands.")

                } catch (e: Exception) {
                    println("Subscription error: ${e.message}")
                    throw e
                }
            } catch (e: Exception) {
                println("BLE Client Error: ${e.message}")
                isRunning.set(false)
            }
        }
    }

    fun sendCommand(message: BLEMessage) {
        val p = peripheral ?: run {
            println("Error sending command: Peripheral is null")
            return
        }
        
        val char = commandChar ?: run {
            println("Error sending command: Command characteristic not resolved")
            return
        }

        if (!isRunning.get()) {
            println("Error sending command: Client is not running (State: ${p.state.value})")
            return
        }

        scope.launch {
            try {
                // Give BlueZ some extra breathing room before the first packet
                delay(500.milliseconds)
                
                val packets = BLECodec.encodeToByteArrayList(message)
                println("Sending ${packets.size} packets...")
                packets.forEachIndexed { index, packet ->
                    var success = false
                    var attempt = 1
                    val maxAttempts = 3
                    
                    while (!success && attempt <= maxAttempts) {
                        try {
                            // Double check connection state before each packet
                            val currentState = p.state.value
                            if (currentState !is State.Connected) {
                                throw IllegalStateException("Lost connection while sending packets (State: $currentState)")
                            }
                            
                            // Use a timeout for each write to detect BlueZ stall
                            withTimeout(5.seconds) {
                                // Switch back to WithoutResponse + higher pacing for Linux/BlueZ stability
                                p.write(char, packet, WriteType.WithoutResponse)
                            }
                            success = true
                        } catch (e: Exception) {
                            if (e is TimeoutCancellationException) {
                                println("Packet ${index + 1} attempt $attempt TIMED OUT")
                            } else if (e is CancellationException) {
                                throw e
                            } else {
                                println("Packet ${index + 1} attempt $attempt failed: ${e.message}")
                            }
                            
                            if (attempt < maxAttempts && (e.message?.contains("Not connected") == true || e is TimeoutCancellationException)) {
                                println("Transient error or timeout detected, retrying in 1s...")
                                delay(1.seconds)
                                attempt++
                            } else {
                                throw e
                            }
                        }
                    }
                    
                    // Pacing: Increased delay for Swing/Linux stability
                    if (index < packets.size - 1) {
                        delay(500.milliseconds)
                    }
                }
                println("Command sent successfully (${packets.size} packets)")
            } catch (e: Exception) {
                if (e is TimeoutCancellationException) {
                    println("Error sending command: TIMEOUT (State: ${p.state.value})")
                    println("Connection stall detected. Triggering reset...")
                    stop()
                } else if (e is CancellationException) {
                    throw e
                } else {
                    println("Error sending command: ${e.message} (State: ${p.state.value})")
                    if (e.message?.contains("Not connected") == true) {
                        println("Connection failure detected. Triggering reset...")
                        stop()
                    }
                }
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
                println("Disconnected from $deviceName")
            } catch (e: Exception) {
                println("Error during disconnect: ${e.message}")
            } finally {
                // Don't cancel the scope here if we want to allow re-start
                // But for now, we cancel it to be safe
                // scope.cancel() 
            }
        }
    }

    suspend fun disconnectSync() {
        isRunning.set(false)
        val p = peripheral
        peripheral = null
        try {
            p?.disconnect()
            println("Synchronously disconnected from $deviceName")
        } catch (e: Exception) {
            println("Error during synchronous disconnect: ${e.message}")
        }
    }
}
