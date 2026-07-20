package hu.infokristaly.bluetoothsmsgateway.client

import com.juul.kable.*
import hu.infokristaly.bluetoothsmsgateway.BleProtocol
import hu.infokristaly.bluetoothsmsgateway.ble.BLECodec
import hu.infokristaly.bluetoothsmsgateway.ble.BLEFramer
import hu.infokristaly.bluetoothsmsgateway.ble.BLEMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class KableBleClient(
    private val deviceName: String = "SMSGW"
) {
    private var peripheral: Peripheral? = null
    private val framer = BLEFramer()
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start(
        onStatusChange: (String) -> Unit = {},
        onEvent: (BLEMessage) -> Unit
    ) {
        scope.launch {
            try {
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
                    .firstOrNull { it.name == deviceName || it.name?.contains(deviceName) == true }

                if (advertisement == null) {
                    println("Device $deviceName not found.")
                    onStatusChange("Not Found")
                    return@launch
                }

                println("Found device: ${advertisement.name} [${advertisement.identifier}]")
                peripheral = Peripheral(advertisement)

                println("Connecting to $deviceName...")
                onStatusChange("Connecting")
                peripheral!!.connect()
                println("Connected to $deviceName")
                onStatusChange("Connected")
                isRunning.set(true)

                // Observe notifications
                val characteristic = characteristicOf(
                    service = BleProtocol.SERVICE_UUID,
                    characteristic = BleProtocol.EVENT_UUID
                )

                println("Subscribing to notifications for ${BleProtocol.EVENT_UUID}...")
                peripheral!!.observe(characteristic)
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
            } catch (e: Exception) {
                println("BLE Client Error: ${e.message}")
                isRunning.set(false)
            }
        }
    }

    fun sendCommand(message: BLEMessage) {
        val p = peripheral ?: return
        if (!isRunning.get()) return

        scope.launch {
            try {
                val characteristic = characteristicOf(
                    service = BleProtocol.SERVICE_UUID,
                    characteristic = BleProtocol.COMMAND_UUID
                )
                
                val packets = BLECodec.encodeToByteArrayList(message)
                packets.forEach { packet ->
                    p.write(characteristic, packet, WriteType.WithoutResponse)
                }
            } catch (e: Exception) {
                println("Error sending command: ${e.message}")
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        scope.launch {
            peripheral?.disconnect()
            println("Disconnected from $deviceName")
            scope.cancel()
        }
    }
}
