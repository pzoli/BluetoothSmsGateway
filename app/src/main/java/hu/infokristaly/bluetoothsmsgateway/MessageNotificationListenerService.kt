package hu.infokristaly.bluetoothsmsgateway

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import hu.infokristaly.bluetoothsmsgateway.ble.BLEMessage
import hu.infokristaly.bluetoothsmsgateway.ble.MessageType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class MessageNotificationListenerService : NotificationListenerService() {

    private val targetPackages = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName !in targetPackages) return

        val notification = sbn.notification
        val extras = notification.extras
        
        // Extract sender and message text
        val sender = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        if (sender != null && text != null) {
            Log.d("NotificationListener", "Intercepted message from $sender: $text")
            
            val event = BLEMessage(
                action = "sms_received", // Reuse the same action for simplicity in client
                type = MessageType.event,
                payload = buildJsonObject {
                    put("from", JsonPrimitive(sender))
                    put("text", JsonPrimitive(text))
                }
            )

            BleServer.instance?.sendEvent(event)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Not needed for now
    }
}
