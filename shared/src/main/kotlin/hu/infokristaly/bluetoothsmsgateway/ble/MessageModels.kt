package hu.infokristaly.bluetoothsmsgateway.ble

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class BLEMessage(
    val id: Long? = null,
    val type: MessageType,
    val action: String? = null,
    val status: Status? = null,
    val payload: JsonElement? = null,
    val error: BLEError? = null
)

@Serializable
data class BLEError(
    val code: String,
    val message: String
)

@Serializable
enum class MessageType {
    request,
    response,
    event
}

@Serializable
enum class Status {
    ok,
    error
}
