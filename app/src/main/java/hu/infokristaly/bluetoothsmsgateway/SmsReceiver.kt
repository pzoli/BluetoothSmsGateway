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

            val messages = pdus.mapNotNull { pdu ->
                (pdu as? ByteArray)?.let { SmsMessage.createFromPdu(it, format) }
            }

            if (messages.isEmpty()) {
                Log.w("SmsReceiver", "No valid SMS messages created from PDUs")
                return
            }

            val sender = messages.first().originatingAddress ?: ""
            val fullText = messages.joinToString("") { it.messageBody ?: "" }

            Log.d("SmsReceiver", "Received SMS from: $sender (length: ${fullText.length})")

            val event = BLEMessage(
                action = "sms_received",
                type = MessageType.event,
                payload = buildJsonObject {
                    put("from", JsonPrimitive(sender))
                    put("text", JsonPrimitive(fullText))
                }
            )

            val server = BleServer.instance
            if (server != null) {
                Log.d("SmsReceiver", "Forwarding event to BleServer")
                server.sendEvent(event)
            } else {
                Log.e("SmsReceiver", "BleServer.instance is null. Service might not be running.")
            }

        }
    }
}
