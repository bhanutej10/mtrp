package dev.mtrp.core.packet

/**
 * MTRP-SPEC-v0.1 Section 5.5 — Message type values.
 * Values match protobuf msg_type field exactly.
 * Author: K. Bhanutej
 */
enum class MsgType(val wireValue: Int) {
    MESSAGE (0x01),  // application message
    RREQ    (0x02),  // route request flood
    RREP    (0x03),  // route reply
    ACK     (0x04),  // delivery acknowledgement
    BEACON  (0x05),  // neighbour discovery broadcast
    REVOKE  (0x06);  // key revocation broadcast (SPEC 3.3)

    companion object {
        fun fromWire(value: Int): MsgType =
            entries.firstOrNull { it.wireValue == value }
                ?: throw IllegalArgumentException(
                    "Unknown msg_type: 0x${value.toString(16)} — SPEC E010")
    }
}

/**
 * MTRP-SPEC-v0.1 Section 4.1 — Channel type wire values.
 * Separate from ChannelType enum — this is the 1-byte wire representation.
 * Author: K. Bhanutej
 */
enum class ChanType(val wireValue: Int) {
    WIFI        (0x01),
    CELLULAR    (0x02),
    WIFI_DIRECT (0x03),
    BLE         (0x04),
    SMS         (0x05),
    LORA        (0x06),
    NOSTR       (0x07);

    companion object {
        fun fromWire(value: Int): ChanType =
            entries.firstOrNull { it.wireValue == value }
                ?: throw IllegalArgumentException(
                    "Unknown chan_type: 0x${value.toString(16)} — SPEC E010")

        fun fromChannelType(ch: dev.mtrp.core.ChannelType): ChanType =
            when (ch) {
                dev.mtrp.core.ChannelType.WIFI        -> WIFI
                dev.mtrp.core.ChannelType.CELLULAR    -> CELLULAR
                dev.mtrp.core.ChannelType.WIFI_DIRECT -> WIFI_DIRECT
                dev.mtrp.core.ChannelType.BLE         -> BLE
                dev.mtrp.core.ChannelType.SMS         -> SMS
                dev.mtrp.core.ChannelType.LORA        -> LORA
                dev.mtrp.core.ChannelType.NOSTR       -> NOSTR
                dev.mtrp.core.ChannelType.QUEUED      ->
                    throw IllegalArgumentException("QUEUED has no wire value")
            }
    }
}
