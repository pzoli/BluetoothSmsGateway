package hu.infokristaly.bluetoothsmsgateway.client

import com.juul.kable.*
import hu.infokristaly.bluetoothsmsgateway.BleProtocol
import hu.infokristaly.bluetoothsmsgateway.ble.BLECodec
import hu.infokristaly.bluetoothsmsgateway.ble.BLEFramer
import hu.infokristaly.bluetoothsmsgateway.ble.BLEMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class KableBleClient {
    private companion object {
        val CONNECT_TIMEOUT = 60.seconds
        val NOTIFICATION_SUBSCRIPTION_TIMEOUT = 20.seconds
        // The Android GATT server creates the bond asynchronously when a new
        // central connects. Its CCCD is encrypted, so writing it immediately
        // can race the pairing handshake and be rejected with
        // GATT_INSUF_AUTHENTICATION.
        val ENCRYPTION_SETTLE_DELAY = 5.seconds
    }

    @Volatile
    private var peripheral: Peripheral? = null
    private val framer = BLEFramer()
    private val isRunning = AtomicBoolean(false)
    private val writeMutex = Mutex()
    /**
     * BlueZ may complete a cancelled connect operation later.  Tag every attempt so
     * a late state notification from an old Peripheral cannot revive the UI.
     */
    private val connectionGeneration = AtomicLong(0)
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        log("CRITICAL BLE ERROR: ${throwable.message}")
        isRunning.set(false)
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exceptionHandler)
    private var onLogCallback: (String) -> Unit = {}
    private var onStatusCallback: (String) -> Unit = {}
    var keypass: String? = null

    private fun log(message: String) {
        println(message)
        onLogCallback(message)
    }

    private fun reportDisconnected(candidate: Peripheral, reason: String) {
        if (peripheral !== candidate) return

        isRunning.set(false)
        log("BlueZ reports that the GATT link is no longer usable: $reason")
        onStatusCallback("Disconnected")
    }

    private var activeJob: Job? = null
    private var disconnectJob: Job? = null

    private fun isCurrent(generation: Long, candidate: Peripheral) =
        connectionGeneration.get() == generation && peripheral === candidate

    private suspend fun disconnect(candidate: Peripheral) {
        // A timeout/cancel leaves the caller's coroutine cancelled. BlueZ still
        // needs an explicit disconnect request in that case; otherwise its native
        // GATT session can survive and poison the next connection attempt.
        withContext(NonCancellable) {
            try {
                withTimeout(3.seconds) {
                    log("DEBUG: Triggering peripheral disconnect...")
                    candidate.disconnect()
                    log("DEBUG: Peripheral disconnect finished.")
                }
            } catch (e: Exception) {
                log("Error during disconnect: ${e.message}")
            } finally {
                // disconnect() only closes the current GATT link. On the
                // JVM/BlueZ backend the native Peripheral and its observers
                // also need disposal, otherwise a later connection can reuse
                // a stale GATT/notification operation ("In Progress").
                try {
                    withTimeout(3.seconds) {
                        log("DEBUG: Closing peripheral resources...")
                        candidate.close()
                        log("DEBUG: Peripheral resources closed.")
                    }
                } catch (e: Exception) {
                    log("Error closing peripheral: ${e.message}")
                }
            }
        }
    }

    fun start(
        onStatusChange: (String) -> Unit = {},
        onLog: (String) -> Unit = {},
        onEvent: (BLEMessage) -> Unit
    ) {
        this.onLogCallback = onLog
        this.onStatusCallback = onStatusChange
        val generation = connectionGeneration.incrementAndGet()
        val previousConnectionJob = activeJob
        val previousDisconnectJob = disconnectJob
        previousConnectionJob?.cancel()
        framer.reset()
        isRunning.set(false)

        activeJob = scope.launch {
            var candidate: Peripheral? = null
            var connectionReady = false
            val notificationReady = AtomicBoolean(false)
            try {
                // BlueZ rejects StartNotify with "In Progress" when a new connection
                // races the previous connection's unsubscribe/disconnect sequence.
                // Never scan again until that sequence has fully settled.
                withTimeoutOrNull(5.seconds) {
                    previousConnectionJob?.join()
                    previousDisconnectJob?.join()
                } ?: run {
                    log("WARNING: Previous connection cleanup timed out. Forcing background disposal.")
                    // Try to force close old peripheral if it still exists
                    val oldP = peripheral
                    if (oldP != null) {
                        GlobalScope.launch { disconnect(oldP) }
                    }
                }

                if (connectionGeneration.get() != generation) return@launch

                if (peripheral != null) {
                    log("Cleaning up old peripheral connection...")
                    val oldPeripheral = peripheral!!
                    peripheral = null
                    disconnect(oldPeripheral)
                    delay(3000.milliseconds)
                }

                if (connectionGeneration.get() != generation) return@launch

                // Give the OS a moment to settle after a manual stop or previous cleanup
                delay(3000.milliseconds)

                // Now we are ready for a real connection attempt
                val scanner = Scanner()
                log("Scanning for devices with Service UUID: ${BleProtocol.SERVICE_UUID} (RSSI selection enabled)...")
                onStatusChange("Scanning")
                
                val scanStartTime = System.currentTimeMillis()
                val seenDuringScan = mutableMapOf<String, Pair<Advertisement, Int>>() // ID -> (Ad, Count)
                val lastSeenTime = mutableMapOf<String, Long>()
                
                withTimeoutOrNull(5.seconds) {
                    scanner.advertisements
                        .filter { ad ->
                            ad.uuids.any { it.toString().equals(BleProtocol.SERVICE_UUID.toString(), ignoreCase = true) }
                        }
                        .onEach { ad ->
                            val id = ad.identifier.toString()
                            val current = seenDuringScan[id]
                            seenDuringScan[id] = ad to ((current?.second ?: 0) + 1)
                            lastSeenTime[id] = System.currentTimeMillis()
                            log("DEBUG: Seen matching device: ${ad.name} [$id] RSSI: ${ad.rssi}")
                        }
                        .collect()
                }
                
                seenDuringScan.forEach { (id, pair) ->
                    log("DEBUG: ID $id seen ${pair.second} times. Last RSSI: ${pair.first.rssi}")
                }

                // Pick the one seen most recently. If tied, pick highest RSSI.
                val advertisement = seenDuringScan.keys
                    .sortedWith(compareByDescending<String> { lastSeenTime[it] }.thenByDescending { seenDuringScan[it]?.first?.rssi })
                    .firstOrNull()
                    ?.let { seenDuringScan[it]?.first }

                if (advertisement == null) {
                    log("Device with Service UUID ${BleProtocol.SERVICE_UUID} not found. Check if phone is Advertising and Bluetooth is ON.")
                    onStatusChange("Not Found")
                    return@launch
                }

                val actualName = advertisement.name ?: "Unknown Device"
                log("Found device: $actualName [${advertisement.identifier}]")
                val p = Peripheral(advertisement)
                candidate = p

                // A previous scan can finish after a newer attempt has started.
                // Disconnect it immediately instead of letting BlueZ keep it alive.
                if (connectionGeneration.get() != generation) {
                    disconnect(p)
                    return@launch
                }
                peripheral = p

                // This is a child of the connection attempt, not of the global client
                // scope. Cancelling/retrying therefore also cancels its state observer.
                launch {
                    log("DEBUG: State observer flow started for ${advertisement.identifier}")
                    p.state.collect { state ->
                        if (!isCurrent(generation, p)) return@collect

                        log("Connection state change: $state")
                        val statusString = when (state) {
                            is State.Connecting -> "Connecting"
                            // A connected transport is not yet a usable client. The
                            // encrypted CCCD write below must complete first.
                            is State.Connected -> if (notificationReady.get()) "Connected" else "Connecting"
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

                log("Connecting to $actualName (timeout 20s for physical link)...")
                onStatusChange("Connecting")
                
                val connectionScope = withTimeout(20.seconds) {
                    val connectedScope = p.connect()
                    // On BlueZ connect() can return before all state transitions have
                    // reached the StateFlow. Do not accept commands until Connected.
                    p.state.filterIsInstance<State.Connected>().first()
                    // Extra stabilization: wait 500ms and check if still connected
                    delay(500.milliseconds)
                    if (p.state.value !is State.Connected) throw IllegalStateException("Link dropped immediately after connect")
                    connectedScope
                }

                if (!isCurrent(generation, p)) return@launch
                
                log("Successfully connected to $actualName, settling...")
                connectionReady = true
                delay(3000.milliseconds) // Post-connect settlement delay

                val commandChar = characteristicOf(
                    service = BleProtocol.SERVICE_UUID,
                    characteristic = BleProtocol.COMMAND_UUID
                )

                // Explicitly trigger pairing on Linux by reading an encrypted characteristic
                // Retry up to 3 times to allow other profiles (HFP/A2DP) to settle
                var authSuccess = false
                for (attempt in 1..3) {
                    log("Reading Auth Challenge (attempt $attempt/3) to trigger pairing request on client OS...")
                    try {
                        withTimeout(5.seconds) {
                            p.read(commandChar)
                        }
                        log("Auth read successful, pairing should be active.")
                        authSuccess = true
                        break
                    } catch (e: Exception) {
                        log("Auth read attempt $attempt failed: ${e.message}")
                        
                        if (p.state.value !is State.Connected || e.message?.contains("v1=Not connected", ignoreCase = true) == true) {
                            log("FATAL: Link lost or v1=Not connected error. Aborting.")
                            reportDisconnected(p, e.message ?: "link lost")
                            disconnect(p)
                            return@launch
                        }

                        if (attempt < 3) {
                            log("Retrying Auth read in 2s...")
                            delay(2000.milliseconds)
                        }
                    }
                }

                if (!authSuccess) {
                    log("WARNING: Auth read failed after 3 attempts, link is still alive. Proceeding with caution.")
                }

                // Android starts bonding asynchronously in its GATT-server
                // connection callback. Do not issue the encrypted CCCD write
                // until that exchange has had time to complete.
                log("Waiting ${ENCRYPTION_SETTLE_DELAY.inWholeSeconds}s for encrypted GATT to settle...")
                delay(ENCRYPTION_SETTLE_DELAY)
                if (!isCurrent(generation, p) || p.state.value !is State.Connected) {
                    if (isCurrent(generation, p)) {
                        reportDisconnected(p, "connection dropped while waiting for encryption")
                        disconnect(p)
                    }
                    return@launch
                }

                val eventChar = characteristicOf(
                    service = BleProtocol.SERVICE_UUID,
                    characteristic = BleProtocol.EVENT_UUID
                )

                log("Subscribing to notifications for ${BleProtocol.EVENT_UUID}...")
                
                // Kable ties connection-bound work to the CoroutineScope returned
                // by connect(). On the JVM/BlueZ backend this is essential: using
                // the client-wide scope can leave a notification subscription tied
                // to a dead native GATT session after a silent disconnect.
                connectionScope.launch {
                    try {
                        p.observe(eventChar, onSubscription = {
                            // Kable invokes this only after the platform-level
                            // subscription (the encrypted CCCD write on Android)
                            // has succeeded. Flow.onStart was too early here.
                            notificationReady.set(true)
                            isRunning.set(true)
                            log("Notification subscription confirmed. Client is now ready for commands.")
                            onStatusChange("Connected")
                        })
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

                // Kable deliberately suppresses a NotConnectedException raised
                // while subscribing. Without this watchdog BlueZ can leave the
                // state flow at Connected even though the GATT link is already
                // gone, and the first command fails much later.
                connectionScope.launch {
                    delay(NOTIFICATION_SUBSCRIPTION_TIMEOUT)
                    if (!notificationReady.get() && isCurrent(generation, p)) {
                        log("Notification subscription was not confirmed within ${NOTIFICATION_SUBSCRIPTION_TIMEOUT.inWholeSeconds}s; discarding stale GATT connection.")
                        reportDisconnected(p, "notification subscription did not complete")
                        disconnect(p)
                    }
                }

            } catch (e: TimeoutCancellationException) {
                if (candidate != null && isCurrent(generation, candidate)) {
                    log("Connection timed out. Check if phone is in range.")
                    onStatusChange("Timeout")
                    isRunning.set(false)
                }
            } catch (e: CancellationException) {
                if (candidate != null && isCurrent(generation, candidate)) {
                    log("BLE Client operation cancelled")
                    isRunning.set(false)
                }
            } catch (e: Exception) {
                if (candidate != null && isCurrent(generation, candidate)) {
                    log("BLE Client Error: ${e.message}")
                    onStatusChange("Error")
                    isRunning.set(false)
                }
            } finally {
                // withTimeout only cancels the coroutine; BlueZ may still have a
                // pending native connection. Explicitly tear it down on every failed
                // attempt so it cannot connect later as a zombie session.
                val p = candidate
                if (!connectionReady && p != null && isCurrent(generation, p)) {
                    peripheral = null
                    disconnect(p)
                }
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
                writeMutex.withLock {
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
                                    // Use WithResponse for the first packet to ensure pairing is triggered
                                    // Subsequent packets can be WithoutResponse for speed
                                    val writeType = if (index == 0) WriteType.WithResponse else WriteType.WithoutResponse
                                    p.write(commandChar, packet, writeType)
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
                }
                log("Command sent successfully (${packets.size} packets)")
            } catch (e: Exception) {
                if (e.message?.contains("not connected", ignoreCase = true) == true || e.message?.contains("v1=Not connected", ignoreCase = true) == true) {
                    reportDisconnected(p, e.message ?: e.javaClass.simpleName)
                }
                log("Error sending command: ${e.message}")
            }
        }
    }

    fun stop() {
        connectionGeneration.incrementAndGet()
        isRunning.set(false)
        val connectionJob = activeJob
        connectionJob?.cancel()
        activeJob = null
        framer.reset()
        val p = peripheral
        peripheral = null
        disconnectJob = scope.launch {
            connectionJob?.join()
            if (p != null) {
                disconnect(p)
                // Give BlueZ time to release the previous notification subscription
                // before another Peripheral is created for the same device.
                delay(1500.milliseconds)
                log("Disconnected from device")
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
            log("Synchronously disconnected from device")
        } catch (e: Exception) {
            log("Error during synchronous disconnect: ${e.message}")
        }
    }
}
