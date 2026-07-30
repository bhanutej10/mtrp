package dev.mtrp.core.queue

import dev.mtrp.core.ChannelType
import dev.mtrp.core.packet.MtrpPacket
import dev.mtrp.core.transport.Peer
import dev.mtrp.core.transport.Transport
import dev.mtrp.core.transport.TransportStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * MTRP-SPEC-v0.1 Section 8 — Store and Forward as Transport
 * Author: K. Bhanutej
 */
class QueuedTransport(
    private val queue: StoreForwardQueue,
    private val localOriginId: String
) : Transport {

    override val type:            ChannelType = ChannelType.QUEUED
    override val maxPayloadBytes: Int         = ChannelType.QUEUED.maxPayloadBytes
    override val relayAllowed:    Boolean     = ChannelType.QUEUED.relayAllowed

    private val _status = MutableStateFlow<TransportStatus>(TransportStatus.CONNECTED)
    override val status: StateFlow<TransportStatus> = _status

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    override val peers: StateFlow<List<Peer>> = _peers

    override val incoming: Flow<MtrpPacket> = emptyFlow()

    override suspend fun start() { _status.value = TransportStatus.CONNECTED }
    override suspend fun stop()  { _status.value = TransportStatus.IDLE }

    override suspend fun send(packet: MtrpPacket, to: Peer): Result<Unit> {
        val enqueued = queue.enqueue(packet, localOriginId)
        return if (enqueued) Result.success(Unit)
               else Result.failure(IllegalStateException("Queue full or origin limit exceeded"))
    }

    override fun isAvailable():        Boolean = true
    override fun estimatedLatencyMs(): Long    = Long.MAX_VALUE
    override fun signalStrength():     Int     = 0
    override fun recentFailureRate():  Float   = 0f
    override fun avgRetryCount():      Float   = 0f
    override fun msSinceLastSuccess(): Long    = 0L
}
