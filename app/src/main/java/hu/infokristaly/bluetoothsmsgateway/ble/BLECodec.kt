package hu.infokristaly.bluetoothsmsgateway.ble

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object BLECodec {

    val json = Json {

        ignoreUnknownKeys = true

        encodeDefaults = false
    }

    fun encode(
        message:BLEMessage
    ):ByteArray{

        return (
                json.encodeToString(message)
                        + "\n"
                )
            .toByteArray()
    }

    fun decode(
        text:String
    ):BLEMessage{

        return json.decodeFromString(text)
    }
}