package dev.mtrp.core.crypto

import com.ionspin.kotlin.crypto.hash.Hash
import dev.mtrp.core.MTRP
import dev.mtrp.core.packet.currentTimeMs

/**
 * MTRP-SPEC-v0.1 Section 7.5 — Symmetric Ratchet
 * Author: K. Bhanutej
 */
class SymmetricRatchet(sendKey: ByteArray, val sessionId: String) {

    private var chainKey: ByteArray
    private var counter:  Long = 0L
    private val skippedKeys = mutableMapOf<Long, SkippedKey>()

    init {
        chainKey = hkdf(sendKey, "mtrp-chain-init", sessionId.encodeToByteArray())
        sendKey.fill(0)
    }

    fun nextMessageKey(): RatchetKey {
        val msgKey   = hkdf(chainKey, "mtrp-msg-key",   sessionId.encodeToByteArray())
        val newChain = hkdf(chainKey, "mtrp-chain-key", sessionId.encodeToByteArray())
        chainKey.fill(0)
        chainKey = newChain
        val currentCounter = counter++
        return RatchetKey(key = msgKey, counter = currentCounter, sessionId = sessionId)
    }

    fun messageKeyForCounter(targetCounter: Long): RatchetKey? {
        val skipped = skippedKeys[targetCounter]
        if (skipped != null) {
            skippedKeys.remove(targetCounter)
            return if (currentTimeMs() < skipped.expiresMs) {
                RatchetKey(skipped.key, targetCounter, sessionId)
            } else {
                null
            }
        }

        if ((targetCounter - counter) > MTRP.MAX_SKIPPED_KEYS) return null

        while (counter < targetCounter) {
            val skippedMsgKey = hkdf(chainKey, "mtrp-msg-key",   sessionId.encodeToByteArray())
            val newChain      = hkdf(chainKey, "mtrp-chain-key", sessionId.encodeToByteArray())
            chainKey.fill(0)
            chainKey = newChain
            if (skippedKeys.size < MTRP.MAX_SKIPPED_KEYS) {
                skippedKeys[counter - 1] = SkippedKey(
                    key       = skippedMsgKey,
                    expiresMs = currentTimeMs() + MTRP.SKIPPED_KEY_TTL_MS
                )
            }
        }

        return nextMessageKey()
    }

    fun requiresRenegotiation(incomingCounter: Long): Boolean =
        (incomingCounter - counter) > MTRP.MAX_SKIPPED_KEYS

    private fun hkdf(ikm: ByteArray, salt: String, info: ByteArray): ByteArray {
        val prk = Hash.sha256((ikm + salt.encodeToByteArray()).toUByteArray()).toByteArray()
        return Hash.sha256((prk + info).toUByteArray()).toByteArray()
    }

    fun destroy() {
        chainKey.fill(0)
        skippedKeys.values.forEach { it.key.fill(0) }
        skippedKeys.clear()
    }
}

data class RatchetKey(
    val key:       ByteArray,
    val counter:   Long,
    val sessionId: String
) {
    fun destroy() = key.fill(0)
}

private data class SkippedKey(val key: ByteArray, val expiresMs: Long)
