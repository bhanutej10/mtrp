package dev.mtrp.core.routing

import dev.mtrp.core.MTRP
import dev.mtrp.core.packet.currentTimeMs
import dev.mtrp.core.packet.fillRandom

/**
 * MTRP-SPEC-v0.1 Section 6.4 — Route Discovery
 *
 * Manages RREQ flood rate limiting and RREP validation.
 *
 * Rate limits enforced:
 *   Max 5 RREQs per origin per minute
 *   Max 20 RREQs total per minute across all origins
 *   Excess: silently dropped
 *   Global limit exceeded: 10-second cooldown
 *
 * Wormhole detection:
 *   A RREP claiming hop_count=1 to a destination not in the
 *   neighbour table MUST be rejected.
 *
 * Author: K. Bhanutej
 */
class RreqFlood(private val neighbourTable: NeighbourTable) {

    // Per-origin RREQ counts in the current minute window
    private val perOriginCount = mutableMapOf<String, Int>()
    private var globalCount    = 0
    private var windowStartMs  = currentTimeMs()
    private var cooldownUntilMs = 0L

    /**
     * Returns true if this RREQ is allowed to be processed or forwarded.
     * Returns false if the rate limit is exceeded — packet MUST be dropped silently.
     */
    fun isAllowed(originId: String): Boolean {
        resetWindowIfNeeded()

        if (currentTimeMs() < cooldownUntilMs) return false

        val originCount = perOriginCount.getOrDefault(originId, 0)
        if (originCount >= MTRP.MAX_RREQ_PER_SOURCE_PER_MIN) return false

        if (globalCount >= MTRP.MAX_RREQ_GLOBAL_PER_MIN) {
            cooldownUntilMs = currentTimeMs() + MTRP.RREQ_COOLDOWN_MS
            return false
        }

        perOriginCount[originId] = originCount + 1
        globalCount++
        return true
    }

    /**
     * Validate a received RREP before caching the route.
     * Returns false if the RREP should be discarded.
     *
     * Wormhole detection: a RREP claiming hop_count=1 to a node
     * that is not a direct neighbour MUST be rejected.
     */
    fun validateRrep(
        destId:   String,
        hopCount: Int,
        rrepSig:  ByteArray
    ): Boolean {
        // Wormhole detection
        if (hopCount == 1 && !neighbourTable.isDirectNeighbour(destId)) {
            return false
        }
        // Signature must be non-empty (actual Ed25519 verify done in MeshRouter)
        if (rrepSig.isEmpty()) return false
        return true
    }

    /** Generate a fresh 8-byte RREQ ID. */
    fun newRreqId(): ByteArray = ByteArray(8).also { fillRandom(it) }

    private fun resetWindowIfNeeded() {
        if (currentTimeMs() - windowStartMs >= 60_000L) {
            perOriginCount.clear()
            globalCount   = 0
            windowStartMs = currentTimeMs()
        }
    }
}
