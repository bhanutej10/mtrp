package dev.mtrp.core.packet

/**
 * Packet serialisation. Expect declaration — platform actuals in
 * androidMain (protobuf) and jvmMain (kotlinx.serialization JSON).
 * Author: K. Bhanutej
 */
expect object PacketCodec {
    fun encode(packet: MtrpPacket): ByteArray
    fun decode(bytes: ByteArray): MtrpPacket?
    fun decodeHeader(bytes: ByteArray): MtrpPacket?
    fun encodePayload(payload: MtrpPayload): ByteArray
    fun decodePayload(bytes: ByteArray): MtrpPayload?
    fun generateMsgId(): ByteArray
    fun generateRoutingId(): ByteArray
    val BROADCAST_ROUTING_ID: ByteArray
}
