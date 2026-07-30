package dev.mtrp.core.api

import dev.mtrp.core.ChannelType
import dev.mtrp.core.channel.ChannelManager
import dev.mtrp.core.crypto.CryptoSession
import dev.mtrp.core.crypto.NodeIdentity
import dev.mtrp.core.crypto.PacketCrypto
import dev.mtrp.core.crypto.PacketIntegrity
import dev.mtrp.core.crypto.RoutingPseudonym
import dev.mtrp.core.crypto.SymmetricRatchet
import dev.mtrp.core.packet.ChanType
import dev.mtrp.core.packet.MsgType
import dev.mtrp.core.packet.MtrpPacket
import dev.mtrp.core.packet.MtrpPayload
import dev.mtrp.core.packet.PacketCodec
import dev.mtrp.core.packet.PacketPadding
import dev.mtrp.core.packet.currentTimeMs
import dev.mtrp.core.queue.StoreForwardQueue
import dev.mtrp.core.routing.MeshRouter
import dev.mtrp.core.transport.Peer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * MTRP Public SDK API
 *
 * This is the single class application code interacts with.
 * Everything below this layer is internal SDK implementation.
 *
 * Usage:
 *   val api = MtrpApi(identity, channelManager, router, queue)
 *   api.start()
 *   api.send("dest_node_id", "Hello".encodeToByteArray())
 *   api.incoming.collect { message -> ... }
 *
 * Author: K. Bhanutej
 */
class MtrpApi(
    private val identity:       NodeIdentity,
    private val channelManager: ChannelManager,
    private val router:         MeshRouter,
    private val queue:          StoreForwardQueue
) {
    // ── Incoming messages ─────────────────────────────────────────

    private val _incoming = MutableSharedFlow<IncomingMessage>(replay = 0, extraBufferCapacity = 64)

    /**
     * Collect this flow to receive messages addressed to this node.
     */
    val incoming: Flow<IncomingMessage> = _incoming

    // ── Active channels ───────────────────────────────────────────

    /**
     * List of currently available transport channels, sorted by priority.
     */
    val activeChannels: StateFlow<List<ChannelType>> = channelManager.activeChannels

    // ── Lifecycle ─────────────────────────────────────────────────

    /**
     * Start all transports. Call once after registering transports.
     */
    suspend fun start() {
        channelManager.startAll()
    }

    /**
     * Stop all transports and release resources.
     */
    suspend fun stop() {
        channelManager.stopAll()
    }

    // ── Sending ───────────────────────────────────────────────────

    /**
     * Send a message to a destination node.
     *
     * Returns SendResult indicating which channel was used,
     * or that the message was queued for later delivery.
     *
     * The payload is encrypted, signed, and padded before sending.
     * The application layer provides raw bytes — all crypto is handled internally.
     */
    suspend fun send(
        destNodeId: String,
        data:       ByteArray,
        batteryPct: Int = 100
    ): SendResult {
        val session  = sessionFor(destNodeId)
        val ratchet  = SymmetricRatchet(session.sendKey.copyOf(), session.sessionId)
        val pseudonym = RoutingPseudonym(session.routingKey.copyOf())

        val routingId = pseudonym.derive(destNodeId)
        val msgId     = PacketCodec.generateMsgId()

        val payload = MtrpPayload(
            originId  = identity.nodeId.encodeToByteArray().take(8).toByteArray(),
            createdAt = currentTimeMs(),
            msgType   = MsgType.MESSAGE,
            appData   = data,
            nonce     = PacketCodec.generateMsgId() + PacketCodec.generateMsgId()
        )

        val template = MtrpPacket(
            routingId   = routingId,
            chanType    = ChanType.WIFI,
            relayMac    = ByteArray(32),
            payload     = ByteArray(0),
            senderSig   = ByteArray(64),
            msgId       = msgId,
            createdAtMs = currentTimeMs()
        )

        val ratchetKey  = ratchet.nextMessageKey()
        val encrypted   = PacketCrypto.encryptPayload(template, payload, ratchetKey)
        ratchetKey.destroy()

        val relayMac = PacketIntegrity.computeRelayMac(encrypted, session.relayMacKey)
        val withMac  = encrypted.copy(relayMac = relayMac)
        val sig      = PacketIntegrity.signPacket(withMac, identity.privateKey)
        val signed   = withMac.copy(senderSig = sig)
        val padded   = PacketPadding.applyPadding(signed)

        val channel = channelManager.bestForSend(padded, batteryPct)

        return if (channel != null) {
            val transport = channelManager.transport(channel)
            val peer      = Peer(nodeId = destNodeId, channel = channel)
            val result    = transport?.send(padded, peer)
            if (result?.isSuccess == true) {
                channelManager.recordSuccess(channel, channel.typicalLatencyMs)
                SendResult.Sent(channel)
            } else {
                channelManager.recordFailure(channel)
                val queued = queue.enqueue(padded, identity.nodeId)
                if (queued) SendResult.Queued else SendResult.Failed("All channels failed")
            }
        } else {
            val queued = queue.enqueue(padded, identity.nodeId)
            if (queued) SendResult.Queued else SendResult.Failed("No channel available and queue full")
        }
    }

    // ── Node info ─────────────────────────────────────────────────

    /**
     * The local node's unique identifier.
     */
    val nodeId: String get() = identity.nodeId

    /**
     * Number of messages currently waiting in the store and forward queue.
     */
    val queueSize: Long get() = queue.totalSize()

    // ── Session management ────────────────────────────────────────

    private val sessions = mutableMapOf<String, CryptoSession>()

    private fun sessionFor(destNodeId: String): CryptoSession {
        return sessions.getOrPut(destNodeId) {
            // Placeholder session — real handshake in Phase 10 (WiFi Direct)
            // and finalised in Phase 9 integration
            CryptoSession(
                localNodeId  = identity.nodeId,
                remoteNodeId = destNodeId,
                handshakeOutput = ByteArray(64).also { it.fill(0x42) }
            )
        }
    }
}

// ── Result types ──────────────────────────────────────────────────

sealed class SendResult {
    data class Sent(val channel: ChannelType) : SendResult()
    object Queued : SendResult()
    data class Failed(val reason: String) : SendResult()
}

data class IncomingMessage(
    val senderNodeId: String,
    val data:         ByteArray,
    val channel:      ChannelType,
    val receivedAtMs: Long = currentTimeMs()
)
