package dev.mtrp.core.packet

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JVM actual — PacketCodec using kotlinx.serialization JSON.
 * Used by the desktop app. Interoperable with the Android protobuf
 * version only when packets are exchanged between two desktop nodes.
 * Android-to-desktop packet exchange requires the relay server to
 * transcode between formats — this is a known limitation addressed
 * in Phase 15 (web client / bridge).
 * Author: K. Bhanutej
 */

@Serializable
private data class PacketJson(
    val version:       Int,
    val routingId:     String,
    val ttl:           Int,
    val chanType:      Int,
    val relayMac:      String,
    val payload:       String,
    val senderSig:     String,
    val pad:           String,
    val msgId:         String,
    val fragmentIndex: Int,
    val fragmentTotal: Int,
    val createdAtMs:   Long
)

@Serializable
private data class PayloadJson(
    val originId:  String,
    val createdAt: Long,
    val msgType:   Int,
    val appData:   String,
    val nonce:     String
)

private val json = Json { ignoreUnknownKeys = true }

private fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(it) }

private fun String.fromHex(): ByteArray {
    val len = length / 2
    return ByteArray(len) { i ->
        substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}

actual object PacketCodec {

    actual fun encode(packet: MtrpPacket): ByteArray {
        val pj = PacketJson(
            version       = packet.version.toInt(),
            routingId     = packet.routingId.toHex(),
            ttl           = packet.ttl.toInt(),
            chanType      = packet.chanType.wireValue,
            relayMac      = packet.relayMac.toHex(),
            payload       = packet.payload.toHex(),
            senderSig     = packet.senderSig.toHex(),
            pad           = packet.pad.toHex(),
            msgId         = packet.msgId.toHex(),
            fragmentIndex = packet.fragmentIndex,
            fragmentTotal = packet.fragmentTotal,
            createdAtMs   = packet.createdAtMs
        )
        return json.encodeToString(pj).encodeToByteArray()
    }

    actual fun decode(bytes: ByteArray): MtrpPacket? {
        return try {
            val pj = json.decodeFromString<PacketJson>(bytes.decodeToString())
            MtrpPacket(
                version       = pj.version.toUByte(),
                routingId     = pj.routingId.fromHex(),
                ttl           = pj.ttl.toUByte(),
                chanType      = ChanType.fromWire(pj.chanType),
                relayMac      = pj.relayMac.fromHex(),
                payload       = pj.payload.fromHex(),
                senderSig     = pj.senderSig.fromHex(),
                pad           = pj.pad.fromHex(),
                msgId         = pj.msgId.fromHex(),
                fragmentIndex = pj.fragmentIndex,
                fragmentTotal = pj.fragmentTotal,
                createdAtMs   = pj.createdAtMs
            )
        } catch (e: Exception) { null }
    }

    actual fun decodeHeader(bytes: ByteArray): MtrpPacket? = decode(bytes)

    actual fun encodePayload(payload: MtrpPayload): ByteArray {
        val pj = PayloadJson(
            originId  = payload.originId.toHex(),
            createdAt = payload.createdAt,
            msgType   = payload.msgType.wireValue,
            appData   = payload.appData.toHex(),
            nonce     = payload.nonce.toHex()
        )
        return json.encodeToString(pj).encodeToByteArray()
    }

    actual fun decodePayload(bytes: ByteArray): MtrpPayload? {
        return try {
            val pj = json.decodeFromString<PayloadJson>(bytes.decodeToString())
            MtrpPayload(
                originId  = pj.originId.fromHex(),
                createdAt = pj.createdAt,
                msgType   = MsgType.fromWire(pj.msgType),
                appData   = pj.appData.fromHex(),
                nonce     = pj.nonce.fromHex()
            )
        } catch (e: Exception) { null }
    }

    actual fun generateMsgId(): ByteArray    = ByteArray(8).also { fillRandom(it) }
    actual fun generateRoutingId(): ByteArray = ByteArray(8).also { fillRandom(it) }
    actual val BROADCAST_ROUTING_ID: ByteArray get() = ByteArray(8) { 0xFF.toByte() }
}
