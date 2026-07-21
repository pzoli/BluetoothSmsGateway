package hu.infokristaly.bluetoothsmsgateway

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
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
        Log.d("SmsReceiver", "onReceive triggered with action: ${intent.action}")

        if ("android.provider.Telephony.SMS_RECEIVED" == intent.getAction()) {

            val bundle =
                intent.extras ?: run {
                    Log.w("SmsReceiver", "Bundle is null")
                    return
                }


            val pdus =
                bundle["pdus"] as? Array<*> ?: run {
                    Log.w("SmsReceiver", "pdus extra is missing or malformed")
                    return
                }

            val format = bundle.getString("format")
            Log.d("SmsReceiver", "Processing ${pdus.size} PDUs with format: $format")

            pdus.forEach {
                val sms = SmsMessage.createFromPdu(
                    it as ByteArray,
                    format
                )
                val sender =
                    sms.originatingAddress
                val text =
                    sms.messageBody
                
                Log.d("SmsReceiver", "Received SMS from: $sender")

                val event = BLEMessage(
                    action = "sms_received",
                    type = MessageType.event,
                    payload = buildJsonObject {
                        put("from", JsonPrimitive(sender ?: ""))
                        put("text", JsonPrimitive(text))
                    }
                )
                
                try {
                    // Try to access instance. If not initialized, it throws UninitializedPropertyAccessException
                    val server = BleServer.instance
                    Log.d("SmsReceiver", "Forwarding event to BleServer")
                    server.sendEvent(event)
                } catch (e: UninitializedPropertyAccessException) {
                    Log.e("SmsReceiver", "BleServer.instance is NOT initialized. App might be killed in background.")
                } catch (e: Exception) {
                    Log.e("SmsReceiver", "Error sending event to BleServer: ${e.message}")
                }
            }

        }
    }
}
