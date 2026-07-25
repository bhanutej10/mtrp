package dev.mtrp.core.crypto

import com.ionspin.kotlin.crypto.auth.Auth
import dev.mtrp.core.packet.fillRandom

/**
 * MTRP-SPEC-v0.1 Section 3.4 — Routing ID Session Pseudonym
 * Author: K. Bhanutej
 */
class RoutingPseudonym(private val routingKey: ByteArray) {

    private val sessionNonce: ByteArray = ByteArray(16).also { fillRandom(it) }

    fun derive(destNodeId: String): ByteArray {
        val input = destNodeId.encodeToByteArray() + sessionNonce
        val hmac  = Auth.auth(input.toUByteArray(), routingKey.toUByteArray()).toByteArray()
        return hmac.copyOfRange(0, 8)
    }

    fun matchesLocalNode(routingId: ByteArray, localNodeId: String): Boolean {
        val expected = derive(localNodeId)
        return expected.contentEquals(routingId)
    }

    fun destroy() { sessionNonce.fill(0) }
}
