package hu.infokristaly.bluetoothsmsgateway

import android.content.Context
import android.telephony.SmsManager


object SmsManagerService {


    fun send(
        context:Context,
        phone:String,
        text:String
    ){


        val sms =
            SmsManager.getDefault()


        sms.sendTextMessage(
            phone,
            null,
            text,
            null,
            null
        )

    }

}