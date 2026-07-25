package dev.mtrp.core.routing

import dev.mtrp.core.ChannelType
import dev.mtrp.core.MTRP
import dev.mtrp.core.packet.MtrpPacket
import dev.mtrp.core.packet.currentTimeMs
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * MTRP-SPEC-v0.1 Section 6 — Routing Algorithm
 *
 * Central coordinator for all routing decisions.
 * Wires together NeighbourTable, RouteTable, Deduplicator,
 * ChannelScorer, and RreqFlood.
 *
 * Transport implementations register themselves via registerTransport().
 * The application layer calls route() to send a packet.
 *
 * Author: K. Bhanutej
 */
class MeshRouter(
    private val localNodeId:    String,
    private val neighbourTable: NeighbourTable = NeighbourTable(),
    private val routeTable:     RouteTable     = RouteTable(),
    private val deduplicator:   Deduplicator   = Deduplicator(),
    private val scorer:         ChannelScorer  = ChannelScorer()
) {
    private val rreqFlood = RreqFlood(neighbourTable)

    // Registered transport stubs — real transports added in Phases 5-10
    private val transports = mutableMapOf<ChannelType, TransportStub>()

    /**
     * Register a transport. Called once per channel during SDK initialisation.
     */
    fun registerTransport(type: ChannelType, stub: TransportStub) {
        transports[type] = stub
    }

    /**
     * Route a packet to its destination.
     * Returns the ChannelType used, or null if no route was available.
     * SPEC 4.4: minimum 5ms scoring execution time enforced.
     */
    suspend fun route(
        packet:     MtrpPacket,
        batteryPct: Int,
        isRelay:    Boolean = false
    ): ChannelType? {
        val startMs = currentTimeMs()

        val result = selectChannel(packet, batteryPct, isRelay)

        // SPEC 4.4: enforce minimum 5ms execution time
        val elapsed = currentTimeMs() - startMs
        if (elapsed < MTRP.SCORING_MIN_TIME_MS) delay(MTRP.SCORING_MIN_TIME_MS - elapsed)

        return result
    }

    /**
     * Process an incoming packet received from a transport.
     * Performs timestamp check, deduplication, TTL check,
     * broadcast check, and relay rate limiting before forwarding.
     */
    suspend fun onPacketReceived(
        packet:      MtrpPacket,
        prevHopId:   String,
        batteryPct:  Int
    ): RelayDecision {
        // Timestamp window check
        if (abs(currentTimeMs() - packet.createdAtMs) > MTRP.PACKET_TIMESTAMP_WINDOW_MS) {
            return RelayDecision.DROP_TIMESTAMP
        }

        // Deduplication — msg_id only
        if (deduplicator.isDuplicate(packet.msgId)) {
            return RelayDecision.DROP_DUPLICATE
        }

        // TTL check — unsigned
        if (packet.ttl <= 0u) {
            return RelayDecision.DROP_TTL_EXPIRED
        }

        // Broadcast relay prohibition
        if (packet.isBroadcast) {
            return RelayDecision.DROP_BROADCAST
        }

        // RREQ rate limiting
        if (!rreqFlood.isAllowed(prevHopId)) {
            return RelayDecision.DROP_RATE_LIMITED
        }

        return RelayDecision.FORWARD
    }

    /**
     * Record a neighbour beacon.
     * SPEC 6.2: locally measured RSSI only, battery_pct advisory.
     */
    fun onBeaconReceived(entry: NeighbourEntry) {
        neighbourTable.upsert(entry)
    }

    /** Returns the best channel for sending (includes SMS). */
    fun bestForSend(
        candidates: List<ChannelContext>,
        batteryPct: Int
    ): ChannelContext? = scorer.rank(candidates, batteryPct, isRelay = false).firstOrNull()

    /** Returns the best channel for relay (excludes SMS). */
    fun bestForRelay(
        candidates: List<ChannelContext>,
        batteryPct: Int
    ): ChannelContext? = scorer.rank(candidates, batteryPct, isRelay = true).firstOrNull()

    /** Record a successful delivery. */
    fun onAckReceived(destId: String, nextHop: String) {
        routeTable.recordSuccess(destId, nextHop)
    }

    /** Record a delivery failure. */
    fun onDeliveryFailed(destId: String, nextHop: String) {
        routeTable.markStale(destId, nextHop)
    }

    fun neighbourTable() = neighbourTable
    fun routeTable()     = routeTable
    fun deduplicator()  = deduplicator

    private fun selectChannel(
        packet:     MtrpPacket,
        batteryPct: Int,
        isRelay:    Boolean
    ): ChannelType? {
        val available = transports.entries
            .filter { it.value.isAvailable() }
            .filter { !isRelay || it.key.relayAllowed }
            .map { (type, stub) ->
                ChannelContext(
                    channel        = type,
                    latencyMs      = stub.estimatedLatencyMs(),
                    failureRate    = stub.recentFailureRate(),
                    avgRetryCount  = stub.avgRetryCount(),
                    msSinceSuccess = stub.msSinceLastSuccess(),
                    peerCount      = neighbourTable.active().size,
                    hopCount       = routeTable.primaryRoute(
                        packet.routingId.joinToString("") { "%02x".format(it) }
                    )?.hopCount ?: 0
                )
            }
        return scorer.rank(available, batteryPct, isRelay).firstOrNull()?.channel
    }
}

/** Decision returned by onPacketReceived for relay nodes. */
enum class RelayDecision {
    FORWARD,
    DROP_TIMESTAMP,
    DROP_DUPLICATE,
    DROP_TTL_EXPIRED,
    DROP_BROADCAST,
    DROP_RATE_LIMITED
}

/**
 * Minimal interface that transport implementations satisfy.
 * Real transports (BLE, WiFi etc.) implement this in Phases 5-10.
 */
interface TransportStub {
    fun isAvailable(): Boolean
    fun estimatedLatencyMs(): Long
    fun recentFailureRate(): Float
    fun avgRetryCount(): Float
    fun msSinceLastSuccess(): Long
}
