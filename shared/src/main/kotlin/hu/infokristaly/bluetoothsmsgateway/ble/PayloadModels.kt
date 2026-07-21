package hu.infokristaly.bluetoothsmsgateway.ble

import kotlinx.serialization.Serializable

@Serializable
data class SendSmsPayload(
    val phone: String,
    val text: String
)

@Serializable
data class SmsReceivedPayload(
    val from: String,
    val text: String
)

@Serializable
data class StatusPayload(
    val battery: Int,
    val network: String,
    val signal: Int
)

@Serializable
data class Contact(
    val name: String,
    val numbers: List<String>
)

@Serializable
data class ContactListPayload(
    val contacts: List<Contact>
)

@Serializable
enum class CallStatus {
    IDLE,
    RINGING,
    OFFHOOK
}

@Serializable
data class CallStatusPayload(
    val status: CallStatus,
    val phoneNumber: String? = null
)
