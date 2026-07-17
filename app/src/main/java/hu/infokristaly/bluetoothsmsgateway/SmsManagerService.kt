package hu.infokristaly.bluetoothsmsgateway

import android.content.Context
import android.telephony.SmsManager


object SmsManagerService {


    fun send(
        context:Context,
        phone:String,
        text:String
    ){

        val smsManager =
            context.getSystemService(SmsManager::class.java)

        val parts = smsManager.divideMessage(text)

        smsManager.sendMultipartTextMessage(
            phone,
            null,
            parts,
            null,
            null
        )
    }

}