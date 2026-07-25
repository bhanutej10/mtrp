package dev.mtrp.core.routing

import dev.mtrp.core.MTRP
import dev.mtrp.core.packet.currentTimeMs

/**
 * MTRP-SPEC-v0.1 Section 6.5 — Deduplication
 *
 * Deduplication key is msg_id ONLY. A packet with a seen msg_id is
 * dropped regardless of which channel it arrived on. This prevents
 * a relay node from re-injecting a packet on a different channel
 * to bypass deduplication.
 *
 * Capacity: 10,000 entries. Entry TTL: 5 minutes.
 * relay_mac verification happens before this check.
 *
 * Author: K. Bhanutej
 */
class Deduplicator {

    private data class Entry(val seenAtMs: Long)

    // LRU-like map: msgId (hex string) → seen timestamp
    private val seen = LinkedHashMap<String, Entry>(
        16, 0.75f, true   // accessOrder=true gives LRU behaviour
    )

    /**
     * Returns true if this msg_id has been seen before.
     * Returns false (and records the msg_id) if this is the first time.
     * SPEC 6.5: relay_mac must be verified before calling this.
     */
    fun isDuplicate(msgId: ByteArray): Boolean {
        evictExpired()
        val key = msgId.toHexKey()
        return if (seen.containsKey(key)) {
            true
        } else {
            if (seen.size >= MTRP.DEDUP_CACHE_SIZE) {
                seen.entries.iterator().also { it.next(); it.remove() }
            }
            seen[key] = Entry(currentTimeMs())
            false
        }
    }

    private fun evictExpired() {
        val cutoff = currentTimeMs() - MTRP.DEDUP_TTL_MS
        seen.entries.removeAll { it.value.seenAtMs < cutoff }
    }

    fun clear() { seen.clear() }

    val size: Int get() = seen.size

    private fun ByteArray.toHexKey(): String =
        joinToString("") { "%02x".format(it) }
}
