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

    fun encodeToByteArrayList(
        message:BLEMessage
    ): List<ByteArray>{

        return (

                split(json.encodeToString(message)
                        + "\n"
                ))

    }

    fun split(
        message: String,
        mtu: Int = 180
    ): List<ByteArray> {

        val bytes = message.toByteArray(Charsets.UTF_8)

        val packets = mutableListOf<ByteArray>()

        var pos = 0

        while (pos < bytes.size) {

            val len = minOf(mtu, bytes.size - pos)

            packets += bytes.copyOfRange(pos, pos + len)

            pos += len
        }

        return packets
    }
    fun decode(
        text:String
    ):BLEMessage{

        return json.decodeFromString(text)
    }
}