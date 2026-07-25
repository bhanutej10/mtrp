package dev.mtrp.core.crypto

import com.ionspin.kotlin.crypto.auth.Auth
import com.ionspin.kotlin.crypto.signature.Signature
import dev.mtrp.core.packet.MtrpPacket
import dev.mtrp.core.packet.currentTimeMs
import dev.mtrp.core.MTRP
import kotlin.math.abs

/**
 * MTRP-SPEC-v0.1 Section 7.6 — Dual Integrity
 * Author: K. Bhanutej
 */
object PacketIntegrity {

    fun computeRelayMac(packet: MtrpPacket, relayMacKey: ByteArray): ByteArray {
        val input = buildRelayMacInput(packet)
        return Auth.auth(input.toUByteArray(), relayMacKey.toUByteArray()).toByteArray()
    }

    fun verifyRelayMac(packet: MtrpPacket, relayMacKey: ByteArray): Boolean {
        if (abs(currentTimeMs() - packet.createdAtMs) > MTRP.PACKET_TIMESTAMP_WINDOW_MS) {
            return false
        }
        return try {
            Auth.authVerify(
                packet.relayMac.toUByteArray(),
                buildRelayMacInput(packet).toUByteArray(),
                relayMacKey.toUByteArray()
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun buildRelayMacInput(packet: MtrpPacket): ByteArray {
        val ts = ByteArray(8).also {
            var t = packet.createdAtMs
            for (i in 7 downTo 0) { it[i] = (t and 0xFF).toByte(); t = t shr 8 }
        }
        val fi = byteArrayOf((packet.fragmentIndex shr 8).toByte(), packet.fragmentIndex.toByte())
        val ft = byteArrayOf((packet.fragmentTotal shr 8).toByte(), packet.fragmentTotal.toByte())
        return byteArrayOf(packet.version.toByte()) +
               packet.routingId +
               byteArrayOf(packet.ttl.toByte()) +
               byteArrayOf(packet.chanType.wireValue.toByte()) +
               ts + fi + ft
    }

    fun signPacket(packet: MtrpPacket, privateKey: ByteArray): ByteArray {
        val content = buildSignContent(packet)
        return Signature.sign(
            content.toUByteArray(),
            privateKey.toUByteArray()
        ).toByteArray().also { content.fill(0) }
    }

    fun verifySenderSig(packet: MtrpPacket, senderPublicKey: ByteArray): Boolean {
        return try {
            val content = buildSignContent(packet)
            Signature.verifyDetached(
                packet.senderSig.toUByteArray(),
                content.toUByteArray(),
                senderPublicKey.toUByteArray()
            )
            content.fill(0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun buildSignContent(packet: MtrpPacket): ByteArray {
        return byteArrayOf(packet.version.toByte()) +
               packet.routingId +
               byteArrayOf(packet.ttl.toByte()) +
               byteArrayOf(packet.chanType.wireValue.toByte()) +
               packet.relayMac +
               packet.payload
    }
}
