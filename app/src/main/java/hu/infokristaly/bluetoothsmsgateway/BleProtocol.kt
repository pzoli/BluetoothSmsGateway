package hu.infokristaly.bluetoothsmsgateway

import java.util.UUID


object BleProtocol {


    val SERVICE_UUID =
        UUID.fromString(
            "7A100000-1234-5678-1234-000000000001"
        )


    val COMMAND_UUID =
        UUID.fromString(
            "7A100001-1234-5678-1234-000000000001"
        )


    val EVENT_UUID =
        UUID.fromString(
            "7A100002-1234-5678-1234-000000000001"
        )
}