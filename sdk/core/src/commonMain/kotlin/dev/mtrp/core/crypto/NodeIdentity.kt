package dev.mtrp.core.crypto

import com.ionspin.kotlin.crypto.signature.Signature
import com.ionspin.kotlin.crypto.hash.Hash

/**
 * MTRP-SPEC-v0.1 Section 3.1 — Node Identity
 * Author: K. Bhanutej
 */
data class NodeIdentity(
    val nodeId:     String,
    val publicKey:  ByteArray,
    val privateKey: ByteArray
) {
    companion object {

        fun generate(): NodeIdentity {
            val keypair    = Signature.keypair()
            val pubBytes   = keypair.publicKey.toByteArray()
            val privBytes  = keypair.secretKey.toByteArray()
            val hash       = Hash.sha256(pubBytes.toUByteArray()).toByteArray()
            val nodeId     = base58Encode(hash).take(22)
            return NodeIdentity(
                nodeId     = nodeId,
                publicKey  = pubBytes,
                privateKey = privBytes
            )
        }

        fun deriveNodeId(publicKey: ByteArray): String {
            val hash = Hash.sha256(publicKey.toUByteArray()).toByteArray()
            return base58Encode(hash).take(22)
        }

        fun fromStoredKeys(publicKey: ByteArray, privateKey: ByteArray): NodeIdentity {
            require(publicKey.size == 32)  { "Ed25519 public key must be 32 bytes" }
            require(privateKey.size == 64) { "Ed25519 private key must be 64 bytes" }
            return NodeIdentity(
                nodeId     = deriveNodeId(publicKey),
                publicKey  = publicKey,
                privateKey = privateKey
            )
        }

        private const val BASE58_ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

        private fun base58Encode(input: ByteArray): String {
            var value = java.math.BigInteger(1, input)
            val base  = java.math.BigInteger.valueOf(58L)
            val sb    = StringBuilder()
            while (value > java.math.BigInteger.ZERO) {
                val (quotient, remainder) = value.divideAndRemainder(base)
                sb.insert(0, BASE58_ALPHABET[remainder.toInt()])
                value = quotient
            }
            input.takeWhile { it == 0.toByte() }.forEach { sb.insert(0, '1') }
            return sb.toString()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is NodeIdentity) return false
        return nodeId == other.nodeId && publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode() = nodeId.hashCode()
    override fun toString() = "NodeIdentity(nodeId=$nodeId)"
    fun destroy() { privateKey.fill(0) }
}
