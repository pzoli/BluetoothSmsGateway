package hu.infokristaly.bluetoothsmsgateway.ble

import kotlinx.serialization.json.encodeToJsonElement

object BLEProtocol {

    fun sendSms(
        id: Long,
        phone: String,
        text: String
    ): BLEMessage {
        return BLEMessage(
            id = id,
            type = MessageType.request,
            action = "send_sms",
            payload = BLECodec.json.encodeToJsonElement(
                SendSmsPayload(phone, text)
            )
        )
    }

    fun smsReceived(
        phone: String,
        text: String
    ): BLEMessage {
        return BLEMessage(
            type = MessageType.event,
            action = "sms_received",
            payload = BLECodec.json.encodeToJsonElement(
                SmsReceivedPayload(phone, text)
            )
        )
    }

    fun getContacts(id: Long): BLEMessage {
        return BLEMessage(
            id = id,
            type = MessageType.request,
            action = "get_contacts"
        )
    }

    fun contactsResponse(id: Long, contacts: List<Contact>): BLEMessage {
        return BLEMessage(
            id = id,
            type = MessageType.response,
            status = Status.ok,
            action = "contacts_list",
            payload = BLECodec.json.encodeToJsonElement(
                ContactListPayload(contacts)
            )
        )
    }

    fun makeCall(id: Long, phone: String): BLEMessage {
        return BLEMessage(
            id = id,
            type = MessageType.request,
            action = "make_call",
            payload = BLECodec.json.encodeToJsonElement(
                SendSmsPayload(phone, "")
            )
        )
    }

    fun hangUp(id: Long): BLEMessage {
        return BLEMessage(
            id = id,
            type = MessageType.request,
            action = "hang_up"
        )
    }

    fun answerCall(id: Long): BLEMessage {
        return BLEMessage(
            id = id,
            type = MessageType.request,
            action = "answer_call"
        )
    }

    fun callStatusEvent(status: CallStatus, phone: String? = null): BLEMessage {
        return BLEMessage(
            type = MessageType.event,
            action = "call_status",
            payload = BLECodec.json.encodeToJsonElement(
                CallStatusPayload(status, phone)
            )
        )
    }

    fun ok(id: Long) = BLEMessage(
        id = id,
        type = MessageType.response,
        status = Status.ok
    )

    fun error(
        id: Long,
        code: String,
        message: String
    ) = BLEMessage(
        id = id,
        type = MessageType.response,
        status = Status.error,
        error = BLEError(code, message)
    )
}
