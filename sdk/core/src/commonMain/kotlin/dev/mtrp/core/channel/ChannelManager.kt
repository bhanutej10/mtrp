package dev.mtrp.core.channel

import dev.mtrp.core.ChannelType
import dev.mtrp.core.MTRP
import dev.mtrp.core.packet.MtrpPacket
import dev.mtrp.core.packet.currentTimeMs
import dev.mtrp.core.routing.ChannelContext
import dev.mtrp.core.routing.ChannelScorer
import dev.mtrp.core.routing.MeshRouter
import dev.mtrp.core.transport.Transport
import dev.mtrp.core.transport.TransportStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * MTRP-SPEC-v0.1 Section 4 — Channel Manager
 *
 * Central manager for all registered transports.
 * Provides bestForSend() and bestForRelay() with the 5ms minimum
 * execution time enforced as required by the spec.
 *
 * Also collects per-transport metrics (latency, failure rate) so
 * ChannelScorer always has up-to-date data to score with.
 *
 * Author: K. Bhanutej
 */
class ChannelManager(private val router: MeshRouter) {

    private val scope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val scorer  = ChannelScorer()

    private val transports = mutableMapOf<ChannelType, Transport>()
    private val metrics    = mutableMapOf<ChannelType, TransportMetrics>()

    private val _activeChannels = MutableStateFlow<List<ChannelType>>(emptyList())
    val activeChannels: StateFlow<List<ChannelType>> = _activeChannels

    /**
     * Register a transport. Called once per channel during init.
     * Also registers it with MeshRouter.
     */
    fun register(transport: Transport) {
        transports[transport.type] = transport
        metrics[transport.type]    = TransportMetrics()
        router.registerTransport(transport.type, transport)
        observeStatus(transport)
    }

    /**
     * Start all registered transports.
     */
    suspend fun startAll() {
        transports.values.forEach { it.start() }
        updateActiveChannels()
    }

    /**
     * Stop all registered transports.
     */
    suspend fun stopAll() {
        transports.values.forEach { it.stop() }
        _activeChannels.value = emptyList()
    }

    /**
     * Select best channel for sending (origin node — includes SMS).
     * SPEC 4.4: enforces minimum 5ms scoring execution time.
     */
    suspend fun bestForSend(
        packet:     MtrpPacket,
        batteryPct: Int
    ): ChannelType? {
        val startMs = currentTimeMs()
        val result  = score(packet, batteryPct, isRelay = false)
        val elapsed = currentTimeMs() - startMs
        if (elapsed < MTRP.SCORING_MIN_TIME_MS) delay(MTRP.SCORING_MIN_TIME_MS - elapsed)
        return result
    }

    /**
     * Select best channel for relay (excludes SMS).
     * SPEC 4.4: enforces minimum 5ms scoring execution time.
     */
    suspend fun bestForRelay(
        packet:     MtrpPacket,
        batteryPct: Int
    ): ChannelType? {
        val startMs = currentTimeMs()
        val result  = score(packet, batteryPct, isRelay = true)
        val elapsed = currentTimeMs() - startMs
        if (elapsed < MTRP.SCORING_MIN_TIME_MS) delay(MTRP.SCORING_MIN_TIME_MS - elapsed)
        return result
    }

    /**
     * Record a successful send on a channel — updates metrics.
     */
    fun recordSuccess(channel: ChannelType, latencyMs: Long) {
        metrics[channel]?.recordSuccess(latencyMs)
    }

    /**
     * Record a failed send on a channel — updates metrics.
     */
    fun recordFailure(channel: ChannelType) {
        metrics[channel]?.recordFailure()
    }

    fun isAvailable(channel: ChannelType): Boolean =
        transports[channel]?.isAvailable() == true

    fun transport(channel: ChannelType): Transport? = transports[channel]

    private fun score(
        packet:     MtrpPacket,
        batteryPct: Int,
        isRelay:    Boolean
    ): ChannelType? {
        val candidates = transports.entries
            .filter { (_, t) -> t.isAvailable() }
            .filter { (type, _) -> !isRelay || type.relayAllowed }
            .map { (type, transport) ->
                val m = metrics[type] ?: TransportMetrics()
                ChannelContext(
                    channel        = type,
                    latencyMs      = transport.estimatedLatencyMs(),
                    failureRate    = m.failureRate(),
                    avgRetryCount  = m.avgRetryCount(),
                    msSinceSuccess = m.msSinceLastSuccess(),
                    peerCount      = router.neighbourTable().active().size,
                    hopCount       = 0
                )
            }
        return scorer.rank(candidates, batteryPct, isRelay).firstOrNull()?.channel
    }

    private fun observeStatus(transport: Transport) {
        scope.launch {
            transport.status.collect {
                updateActiveChannels()
            }
        }
    }

    private fun updateActiveChannels() {
        _activeChannels.value = transports
            .filter { (_, t) -> t.isAvailable() }
            .keys.toList()
            .sortedBy { it.ordinal }
    }
}

/**
 * Rolling metrics per transport — used by ChannelScorer.
 * Tracks last 100 attempts (sliding window).
 */
class TransportMetrics {

    private val window = ArrayDeque<Boolean>(100)   // true = success
    private var lastSuccessMs = 0L
    private var totalRetries  = 0
    private var totalAttempts = 0

    fun recordSuccess(latencyMs: Long) {
        if (window.size >= 100) window.removeFirst()
        window.addLast(true)
        lastSuccessMs = currentTimeMs()
        totalAttempts++
    }

    fun recordFailure() {
        if (window.size >= 100) window.removeFirst()
        window.addLast(false)
        totalAttempts++
        totalRetries++
    }

    fun failureRate(): Float {
        if (window.isEmpty()) return 0f
        return window.count { !it }.toFloat() / window.size
    }

    fun avgRetryCount(): Float =
        if (totalAttempts == 0) 0f else totalRetries.toFloat() / totalAttempts

    fun msSinceLastSuccess(): Long =
        if (lastSuccessMs == 0L) Long.MAX_VALUE else currentTimeMs() - lastSuccessMs
}
