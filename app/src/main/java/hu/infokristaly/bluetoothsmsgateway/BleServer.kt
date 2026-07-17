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
import androidx.core.app.ActivityCompat

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


        server =
            manager.openGattServer(
                context,
                callback
            )


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

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun startAdvertising() {

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

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
                    android.util.Log.d(
                        "BLE",
                        "Advertising started"
                    )
                }

                override fun onStartFailure(errorCode: Int) {
                    android.util.Log.e(
                        "BLE",
                        "Advertising failed: $errorCode"
                    )
                }
            }
        )
    }

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


                val message =
                    String(value)


                processCommand(
                    message
                )


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
        command:String
    ){


        if(command.startsWith("SEND_SMS")){


            val data =
                command.split("|")


            val phone =
                data[1]


            val text =
                data[2]


            SmsManagerService.send(
                context,
                phone,
                text
            )

        }

    }

}