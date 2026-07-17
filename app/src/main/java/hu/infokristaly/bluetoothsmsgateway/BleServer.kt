package hu.infokristaly.bluetoothsmsgateway

import android.Manifest
import android.bluetooth.*
import android.content.Context
import androidx.annotation.RequiresPermission
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import hu.infokristaly.bluetoothsmsgateway.ble.BLECodec
import hu.infokristaly.bluetoothsmsgateway.ble.BLEFramer
import hu.infokristaly.bluetoothsmsgateway.ble.BLEMessage
import hu.infokristaly.bluetoothsmsgateway.ble.SendSmsPayload
import kotlinx.serialization.json.decodeFromJsonElement

class BleServer(
    private val context: Context
) {

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
                BleProtocol.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )


        val command =
            BluetoothGattCharacteristic(
                BleProtocol.COMMAND_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )


        val event =
            BluetoothGattCharacteristic(
                BleProtocol.EVENT_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            )


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
                ParcelUuid(BleProtocol.SERVICE_UUID)
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