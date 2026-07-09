package dev.mtrp.core.packet

import dev.mtrp.core.ChannelType
import dev.mtrp.core.MTRP

/**
 * MTRP-SPEC-v0.1 Section 5 — Packet Format
 *
 * Kotlin representation of an MTRP packet after decoding from wire format.
 * This is NOT the protobuf class — it's a clean Kotlin data class that
 * the rest of the SDK works with. PacketCodec converts between the two.
 *
 * Author: K. Bhanutej
 */
data class MtrpPacket(

    // ── Header fields (relay nodes can see these) ──────────────────────────

    // SPEC 5.2 — Protocol version (currently 0x01)
    val version: UByte = 0x01u,

    // SPEC 3.4 — 8-byte session pseudonym (NOT the real dest node_id)
    val routingId: ByteArray,

    // SPEC 5.2 — Remaining hops. Must decrement as unsigned before relaying.
    var ttl: UByte = MTRP.MAX_HOPS.toUByte(),

    // SPEC 5.2 — Originating transport channel
    val chanType: ChanType,

    // SPEC 7.6 — 32-byte HMAC-SHA256 relay integrity tag
    val relayMac: ByteArray,

    // ── Body fields ───────────────────────────────────────────────────────

    // SPEC 5.2 — XChaCha20-Poly1305 ciphertext (opaque to relay nodes)
    // In Phase 2 this is raw bytes. Phase 3 adds real encryption.
    val payload: ByteArray,

    // SPEC 7.6 — 64-byte Ed25519 signature (only destination verifies)
    val senderSig: ByteArray,

    // SPEC 5.4 — Random padding bytes to reach fixed bucket size
    val pad: ByteArray = ByteArray(0),

    // ── Fragment fields (SPEC 5.6) ────────────────────────────────────────

    // Original message ID — used for fragment reassembly
    val msgId: ByteArray,

    // 0-based index of this fragment (0 if not fragmented)
    val fragmentIndex: Int = 0,

    // Total fragments for this message (1 if not fragmented)
    val fragmentTotal: Int = 1,

    // ── Timestamp (SPEC 5.3) ─────────────────────────────────────────────

    // Unix milliseconds — included in relay_mac to close replay window
    val createdAtMs: Long = currentTimeMs()

) {
    // ── Computed properties ───────────────────────────────────────────────

    /** True if this packet is one fragment of a larger message */
    val isFragmented: Boolean get() = fragmentTotal > 1

    /** True if this is the last fragment */
    val isLastFragment: Boolean get() = fragmentIndex == fragmentTotal - 1

    /** True if this is a broadcast (routing_id = 0xFF * 8)
     * Relay nodes MUST NOT forward broadcasts (SPEC 9.2) */
    val isBroadcast: Boolean
        get() = routingId.size == 8 && routingId.all { it == 0xFF.toByte() }

    /** Total wire size including all fields and padding */
    val totalSize: Int
        get() = 1 + routingId.size + 1 + 1 + relayMac.size +
                payload.size + senderSig.size + pad.size +
                msgId.size + 4 + 4 + 8

    // ── Equality (ByteArray fields need custom equals) ────────────────────

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MtrpPacket) return false
        return version == other.version &&
               routingId.contentEquals(other.routingId) &&
               ttl == other.ttl &&
               chanType == other.chanType &&
               relayMac.contentEquals(other.relayMac) &&
               payload.contentEquals(other.payload) &&
               senderSig.contentEquals(other.senderSig) &&
               msgId.contentEquals(other.msgId) &&
               fragmentIndex == other.fragmentIndex &&
               fragmentTotal == other.fragmentTotal &&
               createdAtMs == other.createdAtMs
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + routingId.contentHashCode()
        result = 31 * result + ttl.hashCode()
        result = 31 * result + chanType.hashCode()
        result = 31 * result + msgId.contentHashCode()
        result = 31 * result + fragmentIndex
        result = 31 * result + fragmentTotal
        return result
    }

    override fun toString(): String =
        "MtrpPacket(msgId=${msgId.toHex()}, chan=$chanType, ttl=$ttl, " +
        "fragmented=$isFragmented [$fragmentIndex/${fragmentTotal}])"
}

/** Decrypted payload contents — only the destination sees this */
data class MtrpPayload(
    val originId: ByteArray,      // SPEC 5.5: real sender node_id
    val createdAt: Long,           // SPEC 5.5: creation Unix ms
    val msgType: MsgType,          // SPEC 5.5: message type
    val appData: ByteArray,        // SPEC 5.5: application data
    val nonce: ByteArray           // SPEC 7.5: 20-byte hybrid nonce
) {
    override fun equals(other: Any?): Boolean {
        if (other !is MtrpPayload) return false
        return originId.contentEquals(other.originId) &&
               createdAt == other.createdAt &&
               msgType == other.msgType &&
               appData.contentEquals(other.appData) &&
               nonce.contentEquals(other.nonce)
    }
    override fun hashCode() = originId.contentHashCode() * 31 + createdAt.hashCode()
}

// Platform-specific time function — implemented in androidMain / desktopMain
expect fun currentTimeMs(): Long

// Extension to hex string — useful for logging and debugging
fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(it) }.take(16) + "..."
