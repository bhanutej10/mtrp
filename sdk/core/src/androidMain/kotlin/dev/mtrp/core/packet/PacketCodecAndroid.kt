package dev.mtrp.core.packet

import com.google.protobuf.ByteString
import dev.mtrp.core.proto.DecryptedPayloadProto
import dev.mtrp.core.proto.MtrpPacketProto

/**
 * Android actual — PacketCodec using protobuf.
 * Author: K. Bhanutej
 */
actual object PacketCodec {

    actual fun encode(packet: MtrpPacket): ByteArray {
        val proto = MtrpPacketProto.newBuilder()
            .setVersion(packet.version.toInt())
            .setRoutingId(ByteString.copyFrom(packet.routingId))
            .setTtl(packet.ttl.toInt())
            .setChanType(packet.chanType.wireValue)
            .setRelayMac(ByteString.copyFrom(packet.relayMac))
            .setPayload(ByteString.copyFrom(packet.payload))
            .setSenderSig(ByteString.copyFrom(packet.senderSig))
            .setPad(ByteString.copyFrom(packet.pad))
            .setMsgId(ByteString.copyFrom(packet.msgId))
            .setFragmentIndex(packet.fragmentIndex)
            .setFragmentTotal(packet.fragmentTotal)
            .setCreatedAtMs(packet.createdAtMs)
            .build()
        return proto.toByteArray()
    }

    actual fun decode(bytes: ByteArray): MtrpPacket? {
        return try {
            val proto = MtrpPacketProto.parseFrom(bytes)
            if (proto.version != 0x01)        return null
            if (proto.routingId.size() != 8)  return null
            if (proto.relayMac.size() != 32)  return null
            if (proto.senderSig.size() != 64) return null
            if (proto.msgId.size() != 8)      return null
            MtrpPacket(
                version       = proto.version.toUByte(),
                routingId     = proto.routingId.toByteArray(),
                ttl           = proto.ttl.toUByte(),
                chanType      = ChanType.fromWire(proto.chanType),
                relayMac      = proto.relayMac.toByteArray(),
                payload       = proto.payload.toByteArray(),
                senderSig     = proto.senderSig.toByteArray(),
                pad           = proto.pad.toByteArray(),
                msgId         = proto.msgId.toByteArray(),
                fragmentIndex = proto.fragmentIndex,
                fragmentTotal = proto.fragmentTotal,
                createdAtMs   = proto.createdAtMs
            )
        } catch (e: Exception) { null }
    }

    actual fun decodeHeader(bytes: ByteArray): MtrpPacket? = decode(bytes)

    actual fun encodePayload(payload: MtrpPayload): ByteArray {
        val proto = DecryptedPayloadProto.newBuilder()
            .setOriginId(ByteString.copyFrom(payload.originId))
            .setCreatedAt(payload.createdAt)
            .setMsgType(payload.msgType.wireValue)
            .setAppData(ByteString.copyFrom(payload.appData))
            .setNonce(ByteString.copyFrom(payload.nonce))
            .build()
        return proto.toByteArray()
    }

    actual fun decodePayload(bytes: ByteArray): MtrpPayload? {
        return try {
            val proto = DecryptedPayloadProto.parseFrom(bytes)
            MtrpPayload(
                originId  = proto.originId.toByteArray(),
                createdAt = proto.createdAt,
                msgType   = MsgType.fromWire(proto.msgType),
                appData   = proto.appData.toByteArray(),
                nonce     = proto.nonce.toByteArray()
            )
        } catch (e: Exception) { null }
    }

    actual fun generateMsgId(): ByteArray    = ByteArray(8).also { fillRandom(it) }
    actual fun generateRoutingId(): ByteArray = ByteArray(8).also { fillRandom(it) }
    actual val BROADCAST_ROUTING_ID: ByteArray get() = ByteArray(8) { 0xFF.toByte() }
}
