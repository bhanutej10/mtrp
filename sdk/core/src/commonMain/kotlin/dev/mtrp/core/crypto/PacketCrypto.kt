package dev.mtrp.core.crypto

import com.ionspin.kotlin.crypto.aead.AuthenticatedEncryptionWithAssociatedData
import dev.mtrp.core.packet.MtrpPacket
import dev.mtrp.core.packet.MtrpPayload
import dev.mtrp.core.packet.PacketCodec
import dev.mtrp.core.packet.fillRandom

/**
 * MTRP-SPEC-v0.1 Section 7.5 — Message Encryption
 * XChaCha20-Poly1305 via libsodium (kalium)
 * Author: K. Bhanutej
 */
object PacketCrypto {

    private const val NONCE_RANDOM_BYTES  = 12
    private const val NONCE_COUNTER_BYTES = 8
    private const val NONCE_TOTAL_BYTES   = NONCE_RANDOM_BYTES + NONCE_COUNTER_BYTES

    fun encryptPayload(
        packet:     MtrpPacket,
        payload:    MtrpPayload,
        ratchetKey: RatchetKey
    ): MtrpPacket {
        val plaintext  = PacketCodec.encodePayload(payload)
        val nonce      = buildNonce(ratchetKey.counter)
        val aad        = buildAad(packet)
        val ciphertext = try {
            AuthenticatedEncryptionWithAssociatedData.xChaCha20Poly1305IetfEncrypt(
                message        = plaintext.toUByteArray(),
                associatedData = aad.toUByteArray(),
                nonce          = nonce.toUByteArray(),
                key            = ratchetKey.key.toUByteArray()
            ).toByteArray()
        } finally {
            plaintext.fill(0)
        }
        return packet.copy(payload = nonce + ciphertext)
    }

    fun decryptPayload(
        packet:     MtrpPacket,
        ratchetKey: RatchetKey
    ): MtrpPayload? {
        if (packet.payload.size <= NONCE_TOTAL_BYTES) return null
        val nonce      = packet.payload.copyOfRange(0, NONCE_TOTAL_BYTES)
        val ciphertext = packet.payload.copyOfRange(NONCE_TOTAL_BYTES, packet.payload.size)
        val aad        = buildAad(packet)
        val plaintext = try {
            AuthenticatedEncryptionWithAssociatedData.xChaCha20Poly1305IetfDecrypt(
                ciphertextAndTag = ciphertext.toUByteArray(),
                associatedData   = aad.toUByteArray(),
                nonce            = nonce.toUByteArray(),
                key              = ratchetKey.key.toUByteArray()
            ).toByteArray()
        } catch (e: Exception) {
            return null
        }
        return PacketCodec.decodePayload(plaintext).also { plaintext.fill(0) }
    }

    fun buildNonce(counter: Long): ByteArray {
        val random       = ByteArray(NONCE_RANDOM_BYTES).also { fillRandom(it) }
        val counterBytes = ByteArray(NONCE_COUNTER_BYTES)
        var c = counter
        for (i in NONCE_COUNTER_BYTES - 1 downTo 0) {
            counterBytes[i] = (c and 0xFF).toByte()
            c = c shr 8
        }
        return random + counterBytes
    }

    fun buildAad(packet: MtrpPacket): ByteArray {
        val ts = ByteArray(8).also {
            var t = packet.createdAtMs
            for (i in 7 downTo 0) { it[i] = (t and 0xFF).toByte(); t = t shr 8 }
        }
        return byteArrayOf(packet.version.toByte()) +
               packet.routingId +
               byteArrayOf(packet.ttl.toByte()) +
               byteArrayOf(packet.chanType.wireValue.toByte()) +
               ts
    }
}
