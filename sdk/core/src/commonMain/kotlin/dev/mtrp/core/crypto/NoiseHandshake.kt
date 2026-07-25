package dev.mtrp.core.crypto

import com.ionspin.kotlin.crypto.scalarmult.ScalarMultiplication
import com.ionspin.kotlin.crypto.signature.Signature
import com.ionspin.kotlin.crypto.hash.Hash
import dev.mtrp.core.MTRP
import dev.mtrp.core.packet.currentTimeMs
import dev.mtrp.core.packet.fillRandom
import kotlin.math.abs

/**
 * MTRP-SPEC-v0.1 Section 7.2 — Noise XX Handshake
 * Author: K. Bhanutej
 */
class NoiseHandshake(private val localIdentity: NodeIdentity) {

    private var ephemeralPublicKey:  ByteArray? = null
    private var ephemeralPrivateKey: ByteArray? = null
    private val transcript = mutableListOf<ByteArray>()
    private var dhEE: ByteArray? = null
    private var dhES: ByteArray? = null
    private var dhSE: ByteArray? = null

    fun buildMessage1(): ByteArray {
        val (pub, priv) = generateEphemeralKeypair()
        ephemeralPublicKey  = pub
        ephemeralPrivateKey = priv
        val timestamp = currentTimeMs()
        val msg = pub + timestamp.toBeBytes()
        transcript.add(msg)
        return msg
    }

    fun processMessage1AndBuildMessage2(msg1: ByteArray): ByteArray {
        val remoteEphPub = msg1.copyOfRange(0, 32)
        val timestamp    = msg1.copyOfRange(32, 40).fromBeBytes()
        require(abs(currentTimeMs() - timestamp) < MTRP.HANDSHAKE_TIMESTAMP_WINDOW_MS) {
            "Handshake message 1 timestamp rejected — SPEC E015"
        }
        transcript.add(msg1)
        val (pub, priv) = generateEphemeralKeypair()
        ephemeralPublicKey  = pub
        ephemeralPrivateKey = priv
        dhEE = dh(ephemeralPrivateKey!!, remoteEphPub)
        val timestamp2 = currentTimeMs()
        val msg2 = pub + localIdentity.publicKey + timestamp2.toBeBytes()
        transcript.add(msg2)
        return msg2
    }

    fun processMessage2AndBuildMessage3(msg2: ByteArray): Pair<ByteArray, ByteArray> {
        val remoteEphPub    = msg2.copyOfRange(0, 32)
        val remoteStaticPub = msg2.copyOfRange(32, 64)
        val timestamp       = msg2.copyOfRange(64, 72).fromBeBytes()
        require(abs(currentTimeMs() - timestamp) < MTRP.HANDSHAKE_TIMESTAMP_WINDOW_MS) {
            "Handshake message 2 timestamp rejected — SPEC E015"
        }
        transcript.add(msg2)
        dhEE = dh(ephemeralPrivateKey!!, remoteEphPub)
        dhES = dh(localIdentity.privateKey.copyOfRange(0, 32), remoteEphPub)
        val timestamp3 = currentTimeMs()
        val msg3 = localIdentity.publicKey + timestamp3.toBeBytes()
        transcript.add(msg3)
        dhSE = dh(ephemeralPrivateKey!!, remoteStaticPub)
        return Pair(msg3, remoteStaticPub)
    }

    fun deriveHandshakeOutput(): ByteArray {
        val ee = dhEE ?: error("dhEE not computed")
        val es = dhES ?: ByteArray(32)
        val se = dhSE ?: ByteArray(32)
        return Hash.sha512((ee + es + se).toUByteArray()).toByteArray()
    }

    fun transcriptHash(localNodeId: String, remoteNodeId: String): ByteArray {
        val combined = transcript.fold(ByteArray(0)) { acc, b -> acc + b } +
                       localNodeId.encodeToByteArray() +
                       remoteNodeId.encodeToByteArray()
        return Hash.sha512(combined.toUByteArray()).toByteArray()
    }

    fun buildConfirmation(remoteNodeId: String): ByteArray {
        val hash = transcriptHash(localIdentity.nodeId, remoteNodeId)
        return Signature.sign(
            hash.toUByteArray(),
            localIdentity.privateKey.toUByteArray()
        ).toByteArray()
    }

    fun verifyConfirmation(
        confirmation: ByteArray,
        remotePubKey: ByteArray,
        remoteNodeId: String
    ): Boolean {
        return try {
            val hash = transcriptHash(remoteNodeId, localIdentity.nodeId)
            Signature.verifyDetached(
                confirmation.toUByteArray(),
                hash.toUByteArray(),
                remotePubKey.toUByteArray()
            )
            true
        } catch (e: Exception) { false }
    }

    fun verifyIdentityBinding(remotePubKey: ByteArray, claimedNodeId: String): Boolean {
        return NodeIdentity.deriveNodeId(remotePubKey) == claimedNodeId
    }

    private fun generateEphemeralKeypair(): Pair<ByteArray, ByteArray> {
        val priv = ByteArray(32).also { fillRandom(it) }
        val pub  = ScalarMultiplication.scalarMultiplicationBase(priv.toUByteArray()).toByteArray()
        return Pair(pub, priv)
    }

    private fun dh(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        return ScalarMultiplication.scalarMultiplication(
            privateKey.toUByteArray(),
            publicKey.toUByteArray()
        ).toByteArray()
    }

    private fun Long.toBeBytes(): ByteArray {
        val result = ByteArray(8)
        var v = this
        for (i in 7 downTo 0) { result[i] = (v and 0xFF).toByte(); v = v shr 8 }
        return result
    }

    private fun ByteArray.fromBeBytes(): Long {
        var result = 0L
        for (b in this) result = (result shl 8) or (b.toLong() and 0xFF)
        return result
    }

    fun destroy() {
        ephemeralPrivateKey?.fill(0)
        dhEE?.fill(0)
        dhES?.fill(0)
        dhSE?.fill(0)
    }
}
