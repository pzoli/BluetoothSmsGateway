package hu.infokristaly.bluetoothsmsgateway

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import androidx.annotation.RequiresPermission
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import hu.infokristaly.bluetoothsmsgateway.ble.BLECodec
import hu.infokristaly.bluetoothsmsgateway.ble.BLEFramer
import hu.infokristaly.bluetoothsmsgateway.ble.BLEMessage
import hu.infokristaly.bluetoothsmsgateway.ble.SendSmsPayload
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import java.util.UUID

@OptIn(ExperimentalUuidApi::class)
class BleServer(
    private val context: Context
) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var instance: BleServer
            private set
            
        // Client Characteristic Configuration Descriptor (CCCD)
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    init {
        instance = this
    }

    private val advertiser by lazy {
        adapter.bluetoothLeAdvertiser
    }

    private val manager =
        context.getSystemService(
            BluetoothManager::class.java
        )


    private val adapter =
        manager.adapter


    private lateinit var server:
            BluetoothGattServer

    private var connectedDevice: BluetoothDevice? = null

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE])
    fun start(){


        val gattServer =
            manager.openGattServer(context, callback)

        if (gattServer == null) {
            Log.e("BLE", "openGattServer returned null")
            return
        }

        server = gattServer

        val service =
            BluetoothGattService(
                BleProtocol.SERVICE_UUID.toJavaUuid(),
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )


        val command =
            BluetoothGattCharacteristic(
                BleProtocol.COMMAND_UUID.toJavaUuid(),
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )


        val event =
            BluetoothGattCharacteristic(
                BleProtocol.EVENT_UUID.toJavaUuid(),
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            )

        // Add CCCD descriptor to the event characteristic
        // This is REQUIRED for clients to subscribe to notifications
        val descriptor = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
        )
        event.addDescriptor(descriptor)

        service.addCharacteristic(command)
        service.addCharacteristic(event)


        server.addService(service)

        startAdvertising()
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    private fun startAdvertising() {

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        adapter.name = "SMSGW"

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(
                ParcelUuid(BleProtocol.SERVICE_UUID.toJavaUuid())
            )
            .build()

        advertiser.startAdvertising(
            settings,
            data,
            object : AdvertiseCallback() {

                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    Log.d(
                        "BLE",
                        "Advertising started"
                    )
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.e(
                        "BLE",
                        "Advertising failed: $errorCode"
                    )
                }
            }
        )
    }

    private val framer =
        BLEFramer()


    private val callback =
        object: BluetoothGattServerCallback(){

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(
                device: BluetoothDevice,
                status: Int,
                newState: Int
            ) {
                Log.d("BLE", "onConnectionStateChange: $status -> $newState")
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connectedDevice = device
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedDevice = null
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId:Int,
                characteristic:BluetoothGattCharacteristic,
                preparedWrite:Boolean,
                responseNeeded:Boolean,
                offset:Int,
                value:ByteArray
            ){


                val messages =
                    framer.append(value)

                for(text in messages){

                    val request =
                        BLECodec.decode(text)

                    processCommand(request)
                }

                server.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    null
                )

            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                Log.d("BLE", "onDescriptorWriteRequest: ${descriptor.uuid}")
                if (CCCD_UUID == descriptor.uuid) {
                    descriptor.value = value
                    if (responseNeeded) {
                        server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    }
                }
            }

        }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendEvent(message: BLEMessage) {

        val device = connectedDevice ?: run {
            Log.w("BLE", "No device connected, event dropped")
            return
        }

        val service =
            server.getService(BleProtocol.SERVICE_UUID.toJavaUuid()) ?: return

        val characteristic =
            service.getCharacteristic(BleProtocol.EVENT_UUID.toJavaUuid()) ?: return

        val packets = BLECodec.encodeToByteArrayList(message)
        Log.d("BLE", "Sending event ${message.action} in ${packets.size} packets")
        
        packets.forEachIndexed { index, packet ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                server.notifyCharacteristicChanged(device, characteristic, false, packet)
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = packet
                server.notifyCharacteristicChanged(device, characteristic, false)
            }
            // Small delay to prevent congestion on older devices or fast packets
            if (packets.size > 1) {
                Thread.sleep(10)
            }
        }
    }

    private fun processCommand(
        message: BLEMessage
    ){
        when(message.action){

            "send_sms"->{

                val payload =
                    BLECodec.json.decodeFromJsonElement<SendSmsPayload>(
                        message.payload!!
                    )

                SmsManagerService.send(

                    context,

                    payload.phone,

                    payload.text
                )
            }
        }
    }

}
