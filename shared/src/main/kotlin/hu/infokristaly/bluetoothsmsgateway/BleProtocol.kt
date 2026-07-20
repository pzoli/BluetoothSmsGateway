package hu.infokristaly.bluetoothsmsgateway

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object BleProtocol {
    val SERVICE_UUID = Uuid.parse("7A100000-1234-5678-1234-000000000001")
    val COMMAND_UUID = Uuid.parse("7A100001-1234-5678-1234-000000000001")
    val EVENT_UUID = Uuid.parse("7A100002-1234-5678-1234-000000000001")
}
