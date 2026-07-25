package dev.mtrp.core.crypto

import com.ionspin.kotlin.crypto.hash.Hash
import dev.mtrp.core.MTRP
import dev.mtrp.core.packet.currentTimeMs

/**
 * MTRP-SPEC-v0.1 Section 7.3 — Directional Key Derivation
 * MTRP-SPEC-v0.1 Section 7.4 — Session State Machine
 * Author: K. Bhanutej
 */
class CryptoSession(
    val localNodeId:  String,
    val remoteNodeId: String,
    handshakeOutput:  ByteArray
) {
    val sendKey:     ByteArray
    val recvKey:     ByteArray
    val relayMacKey: ByteArray
    val routingKey:  ByteArray

    enum class State { ESTABLISHED, RENEGOTIATING, TERMINATED }

    var state: State = State.ESTABLISHED
        private set

    private var messageCount: Long = 0L
    private val createdAtMs:  Long = currentTimeMs()
    val sessionId: String = deriveSessionId(localNodeId, remoteNodeId, createdAtMs)

    init {
        val localBytes  = localNodeId.encodeToByteArray()
        val remoteBytes = remoteNodeId.encodeToByteArray()
        val info        = localBytes + remoteBytes

        sendKey     = hkdf(handshakeOutput, "mtrp-send-v1",    info)
        recvKey     = hkdf(handshakeOutput, "mtrp-recv-v1",    remoteBytes + localBytes)
        relayMacKey = hkdf(handshakeOutput, "mtrp-relay-v1",   info)
        routingKey  = hkdf(handshakeOutput, "mtrp-routing-v1", info)

        handshakeOutput.fill(0)
    }

    fun onMessageSent() {
        messageCount++
        val ageMs = currentTimeMs() - createdAtMs
        if (messageCount >= MTRP.SESSION_MSG_LIMIT || ageMs >= MTRP.SESSION_TIME_LIMIT_MS) {
            state = State.RENEGOTIATING
        }
    }

    val needsRenegotiation: Boolean get() = state == State.RENEGOTIATING

    fun terminate() {
        state = State.TERMINATED
        destroy()
    }

    fun isRenegotiationInitiator(): Boolean = localNodeId < remoteNodeId

    private fun hkdf(ikm: ByteArray, salt: String, info: ByteArray): ByteArray {
        val prk = Hash.sha256((ikm + salt.encodeToByteArray()).toUByteArray()).toByteArray()
        return Hash.sha256((prk + info).toUByteArray()).toByteArray()
    }

    private fun deriveSessionId(local: String, remote: String, ts: Long): String {
        val input = local.encodeToByteArray() + remote.encodeToByteArray() + ts.toString().encodeToByteArray()
        val hash  = Hash.sha256(input.toUByteArray()).toByteArray()
        return hash.take(8).joinToString("") { "%02x".format(it) }
    }

    fun destroy() {
        sendKey.fill(0)
        recvKey.fill(0)
        relayMacKey.fill(0)
        routingKey.fill(0)
    }
}
