package dev.mtrp.core.routing

import dev.mtrp.core.ChannelType
import dev.mtrp.core.MTRP
import dev.mtrp.core.packet.currentTimeMs

/**
 * MTRP-SPEC-v0.1 Section 6.3 — Route Table Structure
 *
 * Stores up to 3 ranked routes per destination.
 * Hard cap of 500 total entries. When full, evicts worst-scoring
 * and oldest entries first.
 *
 * Author: K. Bhanutej
 */
data class RouteEntry(
    val destId:             String,
    val routingId:          String,
    val nextHop:            String,
    val hopCount:           Int,
    val channel:            ChannelType,
    val avgLatencyMs:       Long,          // locally measured
    val rssiToNextHop:      Int,           // hardware-reported
    val successCount:       Long  = 0L,
    val failureCount:       Long  = 0L,
    val consecutiveFails:   Int   = 0,
    val lastSuccessMs:      Long  = 0L,
    val score:              Float = 1.0f,  // lower is better
    val rank:               Int   = 1,     // 1=primary 2=backup 3=backup
    val fullPath:           List<String> = emptyList(),
    val pathChannels:       List<ChannelType> = emptyList(),
    val expiresMs:          Long  = currentTimeMs() + MTRP.ROUTE_TTL_MS,
    val rrepSigVerified:    Boolean = false   // MUST be true before route is used
) {
    val failureRate: Float
        get() {
            val total = successCount + failureCount
            return if (total == 0L) 0f else failureCount.toFloat() / total
        }
}

class RouteTable {

    // destId → list of ranked routes (max 3 per destination)
    private val routes = mutableMapOf<String, MutableList<RouteEntry>>()

    // Blacklisted next-hops with expiry
    private val blacklist = mutableMapOf<String, Long>()

    /**
     * Add or update a route entry.
     * Only routes with rrepSigVerified=true are stored.
     * SPEC 6.4: unverified RREP MUST be discarded.
     */
    fun upsert(entry: RouteEntry) {
        if (!entry.rrepSigVerified) return

        if (totalEntries() >= MTRP.MAX_ROUTE_ENTRIES) evict()

        val list = routes.getOrPut(entry.destId) { mutableListOf() }

        // Replace existing entry for same next-hop, otherwise add
        val idx = list.indexOfFirst { it.nextHop == entry.nextHop }
        if (idx >= 0) list[idx] = entry else list.add(entry)

        // Keep only top 3 by score (lowest score = best)
        list.sortBy { it.score }
        while (list.size > MTRP.MAX_ROUTES_PER_DEST) list.removeLast()

        // Update ranks
        list.forEachIndexed { i, r -> list[i] = r.copy(rank = i + 1) }
    }

    /** Returns the primary route to a destination, if one exists and is not expired. */
    fun primaryRoute(destId: String): RouteEntry? {
        return routesTo(destId).firstOrNull()
    }

    /** Returns all valid routes to a destination sorted by rank. */
    fun routesTo(destId: String): List<RouteEntry> {
        val now  = currentTimeMs()
        val list = routes[destId] ?: return emptyList()
        list.removeAll { it.expiresMs <= now || isBlacklisted(it.nextHop) }
        return list.toList()
    }

    /** Mark a route as stale after ACK timeout. */
    fun markStale(destId: String, nextHop: String) {
        val list = routes[destId] ?: return
        val idx  = list.indexOfFirst { it.nextHop == nextHop }
        if (idx >= 0) {
            val entry = list[idx]
            val newConsec = entry.consecutiveFails + 1
            var newScore  = entry.score + if (newConsec >= 3) 0.5f else 0.1f
            newScore = newScore.coerceAtMost(2.0f)
            list[idx] = entry.copy(
                failureCount     = entry.failureCount + 1,
                consecutiveFails = newConsec,
                score            = newScore,
                expiresMs        = 0L   // mark expired
            )
            // Blacklist next-hop after 5 consecutive failures for 5 minutes
            if (newConsec >= 5) {
                blacklist[nextHop] = currentTimeMs() + 300_000L
            }
        }
    }

    /** Record a successful delivery and refresh the route TTL. */
    fun recordSuccess(destId: String, nextHop: String) {
        val list = routes[destId] ?: return
        val idx  = list.indexOfFirst { it.nextHop == nextHop }
        if (idx >= 0) {
            val entry = list[idx]
            list[idx] = entry.copy(
                successCount     = entry.successCount + 1,
                consecutiveFails = 0,
                lastSuccessMs    = currentTimeMs(),
                expiresMs        = currentTimeMs() + MTRP.ROUTE_TTL_MS
            )
        }
    }

    fun isBlacklisted(nodeId: String): Boolean {
        val exp = blacklist[nodeId] ?: return false
        return if (currentTimeMs() < exp) true else {
            blacklist.remove(nodeId)
            false
        }
    }

    fun remove(destId: String) { routes.remove(destId) }

    fun clear() { routes.clear(); blacklist.clear() }

    fun totalEntries(): Int = routes.values.sumOf { it.size }

    private fun evict() {
        // Remove worst-scoring expired entries first, then oldest
        routes.values.forEach { list ->
            list.removeAll { it.expiresMs <= currentTimeMs() }
        }
        routes.entries.removeAll { it.value.isEmpty() }
        if (totalEntries() >= MTRP.MAX_ROUTE_ENTRIES) {
            routes.values.forEach { it.removeLastOrNull() }
        }
    }
}
