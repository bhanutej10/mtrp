package dev.mtrp.core.packet

import dev.mtrp.core.MTRP
import dev.mtrp.core.proto.MtrpPacketProto
import dev.mtrp.core.proto.DecryptedPayloadProto
import com.google.protobuf.ByteString

/**
 * MTRP-SPEC-v0.1 Section 5 — Packet Format
 * Section 11 — Serialisation (protobuf only, no other format)
 *
 * Encodes MtrpPacket → protobuf bytes (for transmission)
 * Decodes protobuf bytes → MtrpPacket (on receipt)
 *
 * Phase 2: payload field is raw bytes (encryption placeholder).
 * Phase 3: PacketCrypto wraps/unwraps the payload field.
 *
 * Author: K. Bhanutej
 */
object PacketCodec {

    // ── Encoding ──────────────────────────────────────────────────────────

    /**
     * Encode MtrpPacket to protobuf bytes ready for transmission.
     * SPEC Section 11: protobuf is the only permitted serialisation format.
     */
    fun encode(packet: MtrpPacket): ByteArray {
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

    /**
     * Decode protobuf bytes into MtrpPacket.
     * Returns null if bytes are malformed — callers MUST handle null.
     * SPEC 5.2: unknown version bytes MUST be dropped silently.
     */
    fun decode(bytes: ByteArray): MtrpPacket? {
        return try {
            val proto = MtrpPacketProto.parseFrom(bytes)

            // SPEC 5.2: drop unknown version bytes silently
            if (proto.version != 0x01) return null

            // SPEC 5.2: validate field sizes
            if (proto.routingId.size() != 8) return null
            if (proto.relayMac.size() != 32) return null
            if (proto.senderSig.size() != 64) return null
            if (proto.msgId.size() != 8) return null

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
        } catch (e: Exception) {
            null  // malformed packet — drop silently
        }
    }

    // ── Header-only decode (for relay nodes) ──────────────────────────────

    /**
     * Decode only the header fields relay nodes need.
     * Relay nodes MUST NOT attempt to decode the payload.
     * This is deliberately limited to fields 1-5 + 9-12.
     */
    fun decodeHeader(bytes: ByteArray): MtrpPacket? {
        return decode(bytes)  // protobuf decodes all fields but relay only uses header
        // Note: relay code never calls payload.decrypt() — it just passes the bytes through
    }

    // ── Payload encode/decode ─────────────────────────────────────────────

    /**
     * Encode a DecryptedPayload to bytes.
     * Phase 3 will encrypt these bytes before putting them in packet.payload.
     */
    fun encodePayload(payload: MtrpPayload): ByteArray {
        val proto = DecryptedPayloadProto.newBuilder()
            .setOriginId(ByteString.copyFrom(payload.originId))
            .setCreatedAt(payload.createdAt)
            .setMsgType(payload.msgType.wireValue)
            .setAppData(ByteString.copyFrom(payload.appData))
            .setNonce(ByteString.copyFrom(payload.nonce))
            .build()
        return proto.toByteArray()
    }

    /**
     * Decode bytes to MtrpPayload.
     * Phase 3 will decrypt packet.payload before passing to this function.
     */
    fun decodePayload(bytes: ByteArray): MtrpPayload? {
        return try {
            val proto = DecryptedPayloadProto.parseFrom(bytes)
            MtrpPayload(
                originId  = proto.originId.toByteArray(),
                createdAt = proto.createdAt,
                msgType   = MsgType.fromWire(proto.msgType),
                appData   = proto.appData.toByteArray(),
                nonce     = proto.nonce.toByteArray()
            )
        } catch (e: Exception) {
            null
        }
    }

    // ── Msg ID generation ─────────────────────────────────────────────────

    /**
     * Generate an 8-byte msg_id.
     * SPEC 5.2: SHA256(origin_node_id || timestamp_ms || random_4_bytes)[0:8]
     * Phase 3 will use real SHA256. For now — random 8 bytes as placeholder.
     */
    fun generateMsgId(): ByteArray {
        val bytes = ByteArray(8)
        return bytes.also { fillRandom(it) }
    }

    /**
     * Generate an 8-byte routing_id placeholder.
     * Phase 3 will derive this properly from HMAC(routing_key, dest_id || session_nonce).
     */
    fun generateRoutingId(): ByteArray {
        val bytes = ByteArray(8)
        return bytes.also { fillRandom(it) }
    }

    // Broadcast routing_id = 0xFF * 8
    val BROADCAST_ROUTING_ID: ByteArray = ByteArray(8) { 0xFF.toByte() }
}

// Platform-specific secure random fill
expect fun fillRandom(bytes: ByteArray)
