package hu.infokristaly.bluetoothsmsgateway

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage


class SmsReceiver: BroadcastReceiver(){

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(
        context:Context?,
        intent:Intent
    ) {

        if ("android.provider.Telephony.SMS_RECEIVED" == intent.getAction()) {

            val bundle =
                intent.extras ?: return


            val pdus =
                bundle["pdus"] as Array<*>


            pdus.forEach {


                val sms =
                    SmsMessage.createFromPdu(
                        it as ByteArray
                    )


                val sender =
                    sms.originatingAddress


                val text =
                    sms.messageBody


                // TODO:
                // BLE Notification küldése Mac felé


            }

        }
    }
}