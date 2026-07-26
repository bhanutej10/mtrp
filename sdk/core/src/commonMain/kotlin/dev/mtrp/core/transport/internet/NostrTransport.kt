package dev.mtrp.core.transport.internet

import dev.mtrp.core.ChannelType
import dev.mtrp.core.packet.MtrpPacket
import dev.mtrp.core.packet.PacketCodec
import dev.mtrp.core.packet.currentTimeMs
import dev.mtrp.core.packet.fillRandom
import dev.mtrp.core.transport.Peer
import dev.mtrp.core.transport.Transport
import dev.mtrp.core.transport.TransportStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
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
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * MTRP-SPEC-v0.1 Section 4.1 — Nostr transport (priority 7)
 *
 * Sends MTRP packets as Nostr events (NIP-01) to a Nostr relay.
 * The packet bytes are Base64-encoded and stored in the event content field.
 * Uses kind=30078 (application-specific replaceable event).
 *
 * Nostr event structure:
 *   id:      SHA256 of the serialised event (computed by relay)
 *   pubkey:  sender's Nostr pubkey (derived from Ed25519 key)
 *   kind:    30078
 *   tags:    [["d", routing_id_hex], ["t", "mtrp"]]
 *   content: Base64(packet bytes)
 *
 * Author: K. Bhanutej
 */
@OptIn(ExperimentalEncodingApi::class)
class NostrTransport(
    private val relayUrl:    String,
    private val localNodeId: String
) : Transport {

    override val type:            ChannelType = ChannelType.NOSTR
    override val maxPayloadBytes: Int         = type.maxPayloadBytes
    override val relayAllowed:    Boolean     = type.relayAllowed

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _status = MutableStateFlow(TransportStatus.IDLE)
    override val status: StateFlow<TransportStatus> = _status

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    override val peers: StateFlow<List<Peer>> = _peers

    private val incomingChannel = Channel<MtrpPacket>(capacity = 256)
    override val incoming: Flow<MtrpPacket> = incomingChannel.receiveAsFlow()

    private val client = HttpClient(CIO) { install(WebSockets) }
    private var session: io.ktor.websocket.DefaultWebSocketSession? = null

    private var lastSuccessMs  = 0L
    private var recentFailures = 0
    private var totalAttempts  = 0

    override suspend fun start() {
        _status.value = TransportStatus.CONNECTING
        scope.launch {
            try {
                client.webSocket(relayUrl) {
                    _status.value = TransportStatus.CONNECTED
                    session = this

                    // Subscribe to MTRP events on this relay
                    send(Frame.Text(buildSubscribeRequest()))

                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            handleNostrMessage(frame.readText())
                        }
                    }
                }
            } catch (e: Exception) {
                _status.value = TransportStatus.ERROR
            } finally {
                _status.value = TransportStatus.DISCONNECTED
                session = null
            }
        }
    }

    override suspend fun stop() {
        session?.close()
        client.close()
        _status.value = TransportStatus.IDLE
    }

    override suspend fun send(packet: MtrpPacket, to: Peer): Result<Unit> {
        totalAttempts++
        return try {
            val s = session ?: return Result.failure(IllegalStateException("Not connected"))
            val bytes   = PacketCodec.encode(packet)
            val b64     = Base64.encode(bytes)
            val routId  = packet.routingId.joinToString("") { "%02x".format(it) }
            val event   = buildNostrEvent(b64, routId)
            s.send(Frame.Text(event))
            lastSuccessMs = currentTimeMs()
            Result.success(Unit)
        } catch (e: Exception) {
            recentFailures++
            Result.failure(e)
        }
    }

    private fun handleNostrMessage(text: String) {
        // Nostr EVENT message format: ["EVENT", subscriptionId, eventObject]
        if (!text.startsWith("[\"EVENT\"")) return
        try {
            val content  = extractContent(text) ?: return
            val bytes    = Base64.decode(content)
            val packet   = PacketCodec.decode(bytes) ?: return
            incomingChannel.trySend(packet)
        } catch (e: Exception) {
            // Malformed event — discard silently
        }
    }

    private fun buildSubscribeRequest(): String {
        val subId = ByteArray(16).also { fillRandom(it) }
            .joinToString("") { "%02x".format(it) }
        return """["REQ","$subId",{"kinds":[30078],"#t":["mtrp"]}]"""
    }

    private fun buildNostrEvent(content: String, routingId: String): String {
        val createdAt = currentTimeMs() / 1000L
        return """["EVENT",{"kind":30078,"created_at":$createdAt,"tags":[["d","$routingId"],["t","mtrp"]],"content":"$content","pubkey":"$localNodeId","sig":""}]"""
    }

    private fun extractContent(json: String): String? {
        val marker = "\"content\":\""
        val start  = json.indexOf(marker) + marker.length
        val end    = json.indexOf("\"", start)
        return if (start > marker.length && end > start) json.substring(start, end) else null
    }

    override fun isAvailable():        Boolean = _status.value == TransportStatus.CONNECTED
    override fun estimatedLatencyMs(): Long    = type.typicalLatencyMs
    override fun signalStrength():     Int     = 0

    override fun recentFailureRate(): Float =
        if (totalAttempts == 0) 0f else recentFailures.toFloat() / totalAttempts

    override fun avgRetryCount():    Float = 0f
    override fun msSinceLastSuccess(): Long =
        if (lastSuccessMs == 0L) Long.MAX_VALUE else currentTimeMs() - lastSuccessMs
}
