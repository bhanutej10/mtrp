package dev.mtrp.core.routing

import dev.mtrp.core.ChannelType
import dev.mtrp.core.MTRP
import dev.mtrp.core.packet.currentTimeMs

/**
 * MTRP-SPEC-v0.1 Section 6.2 — Neighbour Discovery
 *
 * Stores one entry per discovered neighbour node.
 * Entries expire after 90 seconds (3 missed beacons at 30s interval).
 * BLE MAC rotation every 15 minutes is enforced by the BLE transport,
 * not by this table.
 *
 * Author: K. Bhanutej
 */
data class NeighbourEntry(
    val nodeId:       String,
    val channel:      ChannelType,
    val rssi:         Int,               // hardware-reported signal strength only
    val lastBeaconMs: Long,
    val transports:   List<ChannelType>,
    val platform:     String,
    val batteryPct:   Int? = null,       // advisory only, max 5% weight in scoring
    val expiresMs:    Long = lastBeaconMs + MTRP.NEIGHBOUR_TTL_MS
)

class NeighbourTable {

    private val entries = mutableMapOf<String, NeighbourEntry>()

    /**
     * Record or update a neighbour from a received beacon.
     * SPEC 6.2: MUST NOT trust beacon-reported latency for scoring.
     * battery_pct is advisory only.
     */
    fun upsert(entry: NeighbourEntry) {
        entries[entry.nodeId] = entry
    }

    /** Returns all neighbours that have not yet expired. */
    fun active(): List<NeighbourEntry> {
        val now = currentTimeMs()
        entries.entries.removeAll { it.value.expiresMs <= now }
        return entries.values.toList()
    }

    /** Returns a specific neighbour if present and not expired. */
    fun get(nodeId: String): NeighbourEntry? {
        val entry = entries[nodeId] ?: return null
        if (entry.expiresMs <= currentTimeMs()) {
            entries.remove(nodeId)
            return null
        }
        return entry
    }

    /** True if a node is a direct neighbour. Used for wormhole detection. */
    fun isDirectNeighbour(nodeId: String): Boolean = get(nodeId) != null

    fun remove(nodeId: String) { entries.remove(nodeId) }

    fun clear() { entries.clear() }

    val size: Int get() = entries.size
}
