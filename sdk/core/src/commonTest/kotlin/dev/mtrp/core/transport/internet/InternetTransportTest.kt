package dev.mtrp.core.transport.internet

import dev.mtrp.core.ChannelType
import dev.mtrp.core.packet.ChanType
import dev.mtrp.core.packet.MtrpPacket
import dev.mtrp.core.packet.currentTimeMs
import dev.mtrp.core.transport.TransportStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 5 — Internet transport tests
 * Network tests use offline assertions only — no live server required.
 * Author: K. Bhanutej
 */
class WifiTransportTest {

    private fun makePacket() = MtrpPacket(
        routingId   = ByteArray(8) { it.toByte() },
        chanType    = ChanType.WIFI,
        relayMac    = ByteArray(32),
        payload     = ByteArray(64),
        senderSig   = ByteArray(64),
        msgId       = ByteArray(8) { 0x01 },
        createdAtMs = currentTimeMs()
    )

    @Test
    fun initialStatusIsIdle() {
        val transport = WifiTransport("ws://localhost:8080", "test_node")
        assertEquals(TransportStatus.IDLE, transport.status.value)
    }

    @Test
    fun notAvailableBeforeStart() {
        val transport = WifiTransport("ws://localhost:8080", "test_node")
        assertFalse(transport.isAvailable())
    }

    @Test
    fun channelTypeIsWifi() {
        val transport = WifiTransport("ws://localhost:8080", "test_node")
        assertEquals(ChannelType.WIFI, transport.type)
    }

    @Test
    fun relayAllowedForWifi() {
        val transport = WifiTransport("ws://localhost:8080", "test_node")
        assertTrue(transport.relayAllowed)
    }

    @Test
    fun maxPayloadMatchesSpec() {
        val transport = WifiTransport("ws://localhost:8080", "test_node")
        assertEquals(ChannelType.WIFI.maxPayloadBytes, transport.maxPayloadBytes)
    }

    @Test
    fun sendFailsWhenNotConnected() = kotlinx.coroutines.test.runTest {
        val transport = WifiTransport("ws://localhost:8080", "test_node")
        val result    = transport.send(makePacket(), dev.mtrp.core.transport.Peer("peer", ChannelType.WIFI))
        assertTrue(result.isFailure)
    }

    @Test
    fun failureRateZeroInitially() {
        val transport = WifiTransport("ws://localhost:8080", "test_node")
        assertEquals(0f, transport.recentFailureRate())
    }
}

class CellularTransportTest {

    @Test
    fun channelTypeIsCellular() {
        val transport = CellularTransport("ws://localhost:8080", "test_node")
        assertEquals(ChannelType.CELLULAR, transport.type)
    }

    @Test
    fun relayAllowedForCellular() {
        val transport = CellularTransport("ws://localhost:8080", "test_node")
        assertTrue(transport.relayAllowed)
    }
}

class NostrTransportTest {

    @Test
    fun initialStatusIsIdle() {
        val transport = NostrTransport("ws://localhost:7000", "test_node")
        assertEquals(TransportStatus.IDLE, transport.status.value)
    }

    @Test
    fun channelTypeIsNostr() {
        val transport = NostrTransport("ws://localhost:7000", "test_node")
        assertEquals(ChannelType.NOSTR, transport.type)
    }

    @Test
    fun relayAllowedForNostr() {
        val transport = NostrTransport("ws://localhost:7000", "test_node")
        assertTrue(transport.relayAllowed)
    }

    @Test
    fun notAvailableBeforeStart() {
        val transport = NostrTransport("ws://localhost:7000", "test_node")
        assertFalse(transport.isAvailable())
    }

    @Test
    fun maxPayloadMatchesSpec() {
        val transport = NostrTransport("ws://localhost:7000", "test_node")
        assertEquals(ChannelType.NOSTR.maxPayloadBytes, transport.maxPayloadBytes)
    }
}
