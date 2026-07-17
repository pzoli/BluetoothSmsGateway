package hu.infokristaly.bluetoothsmsgateway.ble

import kotlinx.serialization.Serializable

@Serializable
data class SendSmsPayload(

    val phone:String,

    val text:String
)

@Serializable
data class SmsReceivedPayload(

    val from:String,

    val text:String
)

@Serializable
data class StatusPayload(

    val battery:Int,

    val network:String,

    val signal:Int
)