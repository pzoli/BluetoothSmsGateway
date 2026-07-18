package hu.infokristaly.bluetoothsmsgateway

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import hu.infokristaly.bluetoothsmsgateway.ble.BLEMessage
import hu.infokristaly.bluetoothsmsgateway.ble.MessageType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject


class SmsReceiver: BroadcastReceiver(){

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    override fun onReceive(
        context:Context?,
        intent:Intent
    ) {

        if ("android.provider.Telephony.SMS_RECEIVED" == intent.getAction()) {

            val bundle =
                intent.extras ?: return


            val pdus =
                bundle["pdus"] as Array<*>

            val format = bundle.getString("format")

            pdus.forEach {
                val sms = SmsMessage.createFromPdu(
                    it as ByteArray,
                    format
                )
                val sender =
                    sms.originatingAddress
                val text =
                    sms.messageBody

                val event = BLEMessage(
                    action = "sms_received",
                    type = MessageType.event,
                    payload = buildJsonObject {
                        put("from", JsonPrimitive(sender ?: ""))
                        put("text", JsonPrimitive(text))
                    }
                )
                BleServer.instance.sendEvent(event)
            }

        }
    }
}