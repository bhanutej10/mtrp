package dev.mtrp.core.routing

import dev.mtrp.core.ChannelType
import dev.mtrp.core.MTRP
import dev.mtrp.core.packet.*
import kotlin.test.*

/**
 * Phase 4 — Routing engine tests
 * Author: K. Bhanutej
 */

class NeighbourTableTest {

    private fun entry(nodeId: String, ttlMs: Long = MTRP.NEIGHBOUR_TTL_MS) = NeighbourEntry(
        nodeId       = nodeId,
        channel      = ChannelType.BLE,
        rssi         = -70,
        lastBeaconMs = currentTimeMs(),
        transports   = listOf(ChannelType.BLE),
        platform     = "android",
        expiresMs    = currentTimeMs() + ttlMs
    )

    @Test
    fun upsertAndRetrieve() {
        val table = NeighbourTable()
        table.upsert(entry("node1"))
        assertNotNull(table.get("node1"))
    }

    @Test
    fun expiredEntryReturnsNull() {
        val table = NeighbourTable()
        table.upsert(entry("node1", ttlMs = -1000L))
        assertNull(table.get("node1"), "Expired neighbour MUST return null")
    }

    @Test
    fun isDirectNeighbourTrue() {
        val table = NeighbourTable()
        table.upsert(entry("node1"))
        assertTrue(table.isDirectNeighbour("node1"))
    }

    @Test
    fun isDirectNeighbourFalse() {
        val table = NeighbourTable()
        assertFalse(table.isDirectNeighbour("unknown"))
    }

    @Test
    fun activeReturnsOnlyNonExpired() {
        val table = NeighbourTable()
        table.upsert(entry("live",    ttlMs =  90_000L))
        table.upsert(entry("expired", ttlMs = -1000L))
        val active = table.active()
        assertEquals(1, active.size)
        assertEquals("live", active.first().nodeId)
    }
}

class RouteTableTest {

    private fun route(destId: String, nextHop: String, score: Float = 0.5f) = RouteEntry(
        destId          = destId,
        routingId       = "rid_$destId",
        nextHop         = nextHop,
        hopCount        = 2,
        channel         = ChannelType.WIFI,
        avgLatencyMs    = 50L,
        rssiToNextHop   = -60,
        score           = score,
        rrepSigVerified = true,
        expiresMs       = currentTimeMs() + 30_000L
    )

    @Test
    fun unverifiedRrepDiscarded() {
        val table = RouteTable()
        table.upsert(route("dest1", "hop1").copy(rrepSigVerified = false))
        assertNull(table.primaryRoute("dest1"),
            "SPEC 6.4: unverified RREP MUST be discarded")
    }

    @Test
    fun primaryRouteIsLowestScore() {
        val table = RouteTable()
        table.upsert(route("dest1", "hop1", score = 0.8f))
        table.upsert(route("dest1", "hop2", score = 0.3f))
        table.upsert(route("dest1", "hop3", score = 0.6f))
        assertEquals("hop2", table.primaryRoute("dest1")?.nextHop,
            "Primary route MUST be lowest score")
    }

    @Test
    fun maxThreeRoutesPerDest() {
        val table = RouteTable()
        repeat(5) { i -> table.upsert(route("dest1", "hop$i", score = i * 0.1f)) }
        assertTrue(table.routesTo("dest1").size <= MTRP.MAX_ROUTES_PER_DEST,
            "SPEC 6.3: max 3 routes per destination")
    }

    @Test
    fun recordSuccessResetsConsecFails() {
        val table = RouteTable()
        table.upsert(route("dest1", "hop1"))
        table.markStale("dest1", "hop1")
        table.upsert(route("dest1", "hop1"))
        table.recordSuccess("dest1", "hop1")
        assertEquals(0, table.primaryRoute("dest1")?.consecutiveFails)
    }

    @Test
    fun blacklistAfterFiveConsecFails() {
        val table = RouteTable()
        repeat(5) {
            table.upsert(route("dest1", "hop1").copy(consecutiveFails = it))
            table.markStale("dest1", "hop1")
        }
        assertTrue(table.isBlacklisted("hop1"),
            "SPEC 6.6: node MUST be blacklisted after 5 consecutive failures")
    }
}

class DeduplicatorTest {

    @Test
    fun firstSeenIsNotDuplicate() {
        val dedup = Deduplicator()
        val msgId = ByteArray(8) { it.toByte() }
        assertFalse(dedup.isDuplicate(msgId),
            "First occurrence MUST NOT be treated as duplicate")
    }

    @Test
    fun secondSeenIsDuplicate() {
        val dedup = Deduplicator()
        val msgId = ByteArray(8) { it.toByte() }
        dedup.isDuplicate(msgId)
        assertTrue(dedup.isDuplicate(msgId),
            "Second occurrence MUST be duplicate")
    }

    @Test
    fun differentMsgIdsAreIndependent() {
        val dedup  = Deduplicator()
        val msgId1 = ByteArray(8) { 0x01 }
        val msgId2 = ByteArray(8) { 0x02 }
        dedup.isDuplicate(msgId1)
        assertFalse(dedup.isDuplicate(msgId2),
            "Different msg_ids MUST be independent")
    }

    @Test
    fun cacheSizeRespected() {
        val dedup = Deduplicator()
        repeat(MTRP.DEDUP_CACHE_SIZE + 100) { i ->
            dedup.isDuplicate(ByteArray(8) { (i + it).toByte() })
        }
        assertTrue(dedup.size <= MTRP.DEDUP_CACHE_SIZE + 100,
            "SPEC 6.5: dedup cache must not grow unbounded")
    }
}

class ChannelScorerTest {

    private fun ctx(channel: ChannelType, latencyMs: Long = 50L) = ChannelContext(
        channel        = channel,
        latencyMs      = latencyMs,
        failureRate    = 0f,
        avgRetryCount  = 0f,
        msSinceSuccess = 0L,
        peerCount      = 3,
        hopCount       = 1
    )

    @Test
    fun smsExcludedFromRelayScoring() {
        val scorer  = ChannelScorer()
        val ranked  = scorer.rank(
            listOf(ctx(ChannelType.WIFI), ctx(ChannelType.SMS)),
            batteryPct = 80,
            isRelay    = true
        )
        assertFalse(ranked.any { it.channel == ChannelType.SMS },
            "SPEC 4.1: SMS MUST be excluded from relay scoring")
    }

    @Test
    fun smsIncludedForOriginSend() {
        val scorer = ChannelScorer()
        val ranked = scorer.rank(
            listOf(ctx(ChannelType.BLE), ctx(ChannelType.SMS)),
            batteryPct = 80,
            isRelay    = false
        )
        assertTrue(ranked.any { it.channel == ChannelType.SMS },
            "SMS MUST be available for origin node sends")
    }

    @Test
    fun lowerLatencyScoresBetter() {
        val scorer = ChannelScorer()
        val fast   = ctx(ChannelType.WIFI, latencyMs = 10L)
        val slow   = ctx(ChannelType.CELLULAR, latencyMs = 500L)
        val fastScore = scorer.score(fast, 80)
        val slowScore = scorer.score(slow, 80)
        assertTrue(fastScore < slowScore,
            "Lower latency MUST produce lower (better) score")
    }

    @Test
    fun highFailureRateScoresWorse() {
        val scorer  = ChannelScorer()
        val good    = ctx(ChannelType.WIFI).copy(failureRate = 0f)
        val failing = ctx(ChannelType.WIFI).copy(failureRate = 0.8f)
        assertTrue(scorer.score(good, 80) < scorer.score(failing, 80),
            "High failure rate MUST produce worse (higher) score")
    }
}

class RreqFloodTest {

    @Test
    fun firstRreqAllowed() {
        val flood = RreqFlood(NeighbourTable())
        assertTrue(flood.isAllowed("origin1"))
    }

    @Test
    fun perSourceLimitEnforced() {
        val flood = RreqFlood(NeighbourTable())
        repeat(MTRP.MAX_RREQ_PER_SOURCE_PER_MIN) { flood.isAllowed("origin1") }
        assertFalse(flood.isAllowed("origin1"),
            "SPEC 6.4: per-source RREQ limit MUST be enforced")
    }

    @Test
    fun wormholeDetected() {
        val neighbours = NeighbourTable()
        val flood       = RreqFlood(neighbours)
        // dest not in neighbour table but RREP claims hop_count=1
        assertFalse(flood.validateRrep("unknown_dest", hopCount = 1, rrepSig = ByteArray(64)),
            "SPEC 6.4: hop_count=1 to non-neighbour MUST be rejected (wormhole)")
    }

    @Test
    fun validRrepAccepted() {
        val neighbours = NeighbourTable()
        neighbours.upsert(NeighbourEntry(
            nodeId       = "dest1",
            channel      = ChannelType.WIFI,
            rssi         = -50,
            lastBeaconMs = currentTimeMs(),
            transports   = listOf(ChannelType.WIFI),
            platform     = "android"
        ))
        val flood = RreqFlood(neighbours)
        assertTrue(flood.validateRrep("dest1", hopCount = 1, rrepSig = ByteArray(64)),
            "Valid RREP to direct neighbour MUST be accepted")
    }

    @Test
    fun emptySigRejected() {
        val flood = RreqFlood(NeighbourTable())
        assertFalse(flood.validateRrep("dest1", hopCount = 3, rrepSig = ByteArray(0)),
            "RREP with empty signature MUST be rejected")
    }
}

class MeshRouterTest {

    private fun makePacket(ttl: UByte = 10u, broadcast: Boolean = false): MtrpPacket {
        val routingId = if (broadcast) ByteArray(8) { 0xFF.toByte() }
                        else ByteArray(8) { it.toByte() }
        return MtrpPacket(
            routingId   = routingId,
            chanType    = dev.mtrp.core.packet.ChanType.WIFI,
            relayMac    = ByteArray(32),
            payload     = ByteArray(32),
            senderSig   = ByteArray(64),
            msgId       = ByteArray(8) { (0xAB + it).toByte() },
            ttl         = ttl,
            createdAtMs = currentTimeMs()
        )
    }

    @Test
    fun validPacketForwards() = kotlinx.coroutines.test.runTest {
        val router   = MeshRouter("local_node")
        val decision = router.onPacketReceived(makePacket(), "prev_hop", batteryPct = 80)
        assertEquals(RelayDecision.FORWARD, decision)
    }

    @Test
    fun expiredTimestampDropped() = kotlinx.coroutines.test.runTest {
        val router     = MeshRouter("local_node")
        val oldPacket  = makePacket().copy(
            createdAtMs = currentTimeMs() - (MTRP.PACKET_TIMESTAMP_WINDOW_MS + 1000L)
        )
        val decision = router.onPacketReceived(oldPacket, "prev_hop", 80)
        assertEquals(RelayDecision.DROP_TIMESTAMP, decision)
    }

    @Test
    fun duplicateDropped() = kotlinx.coroutines.test.runTest {
        val router = MeshRouter("local_node")
        val packet = makePacket()
        router.onPacketReceived(packet, "prev_hop", 80)
        val decision = router.onPacketReceived(packet, "prev_hop", 80)
        assertEquals(RelayDecision.DROP_DUPLICATE, decision)
    }

    @Test
    fun ttlZeroDropped() = kotlinx.coroutines.test.runTest {
        val router   = MeshRouter("local_node")
        val decision = router.onPacketReceived(makePacket(ttl = 0u), "prev_hop", 80)
        assertEquals(RelayDecision.DROP_TTL_EXPIRED, decision)
    }

    @Test
    fun broadcastDropped() = kotlinx.coroutines.test.runTest {
        val router   = MeshRouter("local_node")
        val decision = router.onPacketReceived(makePacket(broadcast = true), "prev_hop", 80)
        assertEquals(RelayDecision.DROP_BROADCAST, decision)
    }
}
