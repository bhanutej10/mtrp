package dev.mtrp.core.transport.internet

import dev.mtrp.core.ChannelType
import dev.mtrp.core.packet.MtrpPacket
import dev.mtrp.core.packet.PacketCodec
import dev.mtrp.core.packet.currentTimeMs
import dev.mtrp.core.transport.Peer
import dev.mtrp.core.transport.Transport
import dev.mtrp.core.transport.TransportStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * MTRP-SPEC-v0.1 Section 4.1 — WiFi transport (priority 1)
 *
 * Sends and receives MTRP packets via WebSocket over WiFi or Cellular.
 * Uses Ktor CIO engine. Each packet is a binary WebSocket frame.
 * Relay is allowed on both WiFi and Cellular.
 *
 * Author: K. Bhanutej
 */
open class WifiTransport(
    private val serverUrl:  String,
    private val localNodeId: String,
    override val type: ChannelType = ChannelType.WIFI
) : Transport {

    override val maxPayloadBytes: Int    = type.maxPayloadBytes
    override val relayAllowed:    Boolean = type.relayAllowed

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _status = MutableStateFlow(TransportStatus.IDLE)
    override val status: StateFlow<TransportStatus> = _status

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    override val peers: StateFlow<List<Peer>> = _peers

    private val incomingChannel = Channel<MtrpPacket>(capacity = 256)
    override val incoming: Flow<MtrpPacket> = incomingChannel.receiveAsFlow()

    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    private var sendChannel: io.ktor.websocket.DefaultWebSocketSession? = null
    private var lastSuccessMs = 0L
    private var recentFailures = 0
    private var totalAttempts  = 0

    override suspend fun start() {
        _status.value = TransportStatus.CONNECTING
        scope.launch {
            try {
                client.webSocket(serverUrl) {
                    _status.value = TransportStatus.CONNECTED
                    sendChannel   = this

                    // Announce presence
                    send(Frame.Binary(true, buildAnnounceFrame()))

                    // Receive loop
                    for (frame in incoming) {
                        if (frame is Frame.Binary) {
                            val packet = PacketCodec.decode(frame.readBytes())
                            if (packet != null) {
                                incomingChannel.trySend(packet)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _status.value = TransportStatus.ERROR
            } finally {
                _status.value = TransportStatus.DISCONNECTED
                sendChannel   = null
            }
        }
    }

    override suspend fun stop() {
        sendChannel?.close()
        client.close()
        _status.value = TransportStatus.IDLE
    }

    override suspend fun send(packet: MtrpPacket, to: Peer): Result<Unit> {
        totalAttempts++
        return try {
            val session = sendChannel
                ?: return Result.failure(IllegalStateException("Not connected"))
            val bytes = PacketCodec.encode(packet)
            session.send(Frame.Binary(true, bytes))
            lastSuccessMs = currentTimeMs()
            Result.success(Unit)
        } catch (e: Exception) {
            recentFailures++
            Result.failure(e)
        }
    }

    override fun isAvailable(): Boolean = _status.value == TransportStatus.CONNECTED
    override fun estimatedLatencyMs(): Long = type.typicalLatencyMs
    override fun signalStrength(): Int = 0

    override fun recentFailureRate(): Float =
        if (totalAttempts == 0) 0f else recentFailures.toFloat() / totalAttempts

    override fun avgRetryCount(): Float = 0f

    override fun msSinceLastSuccess(): Long =
        if (lastSuccessMs == 0L) Long.MAX_VALUE else currentTimeMs() - lastSuccessMs

    private fun buildAnnounceFrame(): ByteArray =
        """{"node_id":"$localNodeId","type":"${type.name}"}""".encodeToByteArray()
}
