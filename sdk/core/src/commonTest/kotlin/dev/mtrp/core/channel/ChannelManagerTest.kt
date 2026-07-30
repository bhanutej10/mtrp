package dev.mtrp.core.channel

import dev.mtrp.core.ChannelType
import dev.mtrp.core.routing.MeshRouter
import dev.mtrp.core.transport.Peer
import dev.mtrp.core.transport.Transport
import dev.mtrp.core.transport.TransportStatus
import dev.mtrp.core.packet.MtrpPacket
import dev.mtrp.core.packet.ChanType
import dev.mtrp.core.packet.currentTimeMs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 9 — Channel manager tests
 * Author: K. Bhanutej
 */

class FakeTransport(
    override val type: ChannelType,
    private val available: Boolean = true
) : Transport {
    override val maxPayloadBytes: Int     = type.maxPayloadBytes
    override val relayAllowed:    Boolean = type.relayAllowed

    private val _status = MutableStateFlow<TransportStatus>(
        if (available) TransportStatus.CONNECTED else TransportStatus.IDLE
    )
    override val status: StateFlow<TransportStatus> = _status
    override val peers:  StateFlow<List<Peer>>      = MutableStateFlow(emptyList())
    override val incoming: Flow<MtrpPacket>         = emptyFlow()

    override suspend fun start() { _status.value = TransportStatus.CONNECTED }
    override suspend fun stop()  { _status.value = TransportStatus.IDLE }

    override suspend fun send(packet: MtrpPacket, to: Peer): Result<Unit> =
        if (available) Result.success(Unit) else Result.failure(Exception("unavailable"))

    override fun isAvailable():        Boolean = available
    override fun estimatedLatencyMs(): Long    = type.typicalLatencyMs
    override fun signalStrength():     Int     = 0
    override fun recentFailureRate():  Float   = 0f
    override fun avgRetryCount():      Float   = 0f
    override fun msSinceLastSuccess(): Long    = 0L
}

class ChannelManagerTest {

    private fun makeManager(): ChannelManager {
        val router = MeshRouter("test_node")
        return ChannelManager(router)
    }

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
    fun registerTransportMakesItAvailable() {
        val manager = makeManager()
        manager.register(FakeTransport(ChannelType.WIFI))
        assertTrue(manager.isAvailable(ChannelType.WIFI))
    }

    @Test
    fun unavailableTransportNotAvailable() {
        val manager = makeManager()
        manager.register(FakeTransport(ChannelType.WIFI, available = false))
        assertFalse(manager.isAvailable(ChannelType.WIFI))
    }

    @Test
    fun bestForSendExcludesUnavailable() = runTest {
        val manager = makeManager()
        manager.register(FakeTransport(ChannelType.WIFI,     available = false))
        manager.register(FakeTransport(ChannelType.CELLULAR, available = true))
        val best = manager.bestForSend(makePacket(), 80)
        assertEquals(ChannelType.CELLULAR, best)
    }

    @Test
    fun bestForRelayExcludesSms() = runTest {
        val manager = makeManager()
        manager.register(FakeTransport(ChannelType.SMS,  available = true))
        manager.register(FakeTransport(ChannelType.WIFI, available = true))
        val best = manager.bestForRelay(makePacket(), 80)
        assertEquals(ChannelType.WIFI, best,
            "SPEC 4.1: SMS MUST be excluded from relay channel selection")
    }

    @Test
    fun noChannelsReturnsNull() = runTest {
        val manager = makeManager()
        val best = manager.bestForSend(makePacket(), 80)
        assertNull(best)
    }

    @Test
    fun transportRetrievable() {
        val manager   = makeManager()
        val transport = FakeTransport(ChannelType.BLE)
        manager.register(transport)
        assertNotNull(manager.transport(ChannelType.BLE))
    }
}

class TransportMetricsTest {

    @Test
    fun initialFailureRateIsZero() {
        val metrics = TransportMetrics()
        assertEquals(0f, metrics.failureRate())
    }

    @Test
    fun failureRateCalculatedCorrectly() {
        val metrics = TransportMetrics()
        repeat(8) { metrics.recordSuccess(50L) }
        repeat(2) { metrics.recordFailure() }
        assertEquals(0.2f, metrics.failureRate(), absoluteTolerance = 0.01f,
            message = "2 failures out of 10 = 20% failure rate")
    }

    @Test
    fun windowCapsAt100() {
        val metrics = TransportMetrics()
        repeat(110) { metrics.recordSuccess(50L) }
        repeat(10)  { metrics.recordFailure() }
        assertTrue(metrics.failureRate() > 0f)
        assertTrue(metrics.failureRate() <= 1f)
    }

    @Test
    fun msSinceSuccessMaxBeforeAnySuccess() {
        val metrics = TransportMetrics()
        assertEquals(Long.MAX_VALUE, metrics.msSinceLastSuccess())
    }

    @Test
    fun msSinceSuccessNearZeroAfterSuccess() {
        val metrics = TransportMetrics()
        metrics.recordSuccess(50L)
        assertTrue(metrics.msSinceLastSuccess() < 1000L)
    }
}
