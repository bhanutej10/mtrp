package dev.mtrp.core.beacon

import dev.mtrp.core.ChannelType
import dev.mtrp.core.MTRP
import dev.mtrp.core.channel.ChannelManager
import dev.mtrp.core.crypto.NodeIdentity
import dev.mtrp.core.packet.ChanType
import dev.mtrp.core.packet.MtrpPacket
import dev.mtrp.core.packet.PacketCodec
import dev.mtrp.core.packet.currentTimeMs
import dev.mtrp.core.routing.NeighbourEntry
import dev.mtrp.core.routing.NeighbourTable
import dev.mtrp.core.transport.Peer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * MTRP-SPEC-v0.1 Section 6.2 — Beacon Broadcasting
 *
 * Broadcasts a beacon every 30 seconds on all active transports.
 * The beacon announces this node's presence and available transports
 * so neighbouring nodes can update their NeighbourTable.
 *
 * Beacon packet uses TTL=1 and isBroadcast=true so relay nodes
 * drop it after one hop — beacons are local-only by design.
 *
 * Author: K. Bhanutej
 */
class BeaconBroadcaster(
    private val identity:       NodeIdentity,
    private val channelManager: ChannelManager,
    private val neighbourTable: NeighbourTable
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        scope.launch {
            while (isActive) {
                broadcast()
                delay(MTRP.BEACON_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        scope.launch { }.cancel()
    }

    private suspend fun broadcast() {
        val active = channelManager.activeChannels.value
        if (active.isEmpty()) return

        val beacon = buildBeaconPacket(active)

        active.forEach { channel ->
            val transport = channelManager.transport(channel) ?: return@forEach
            val peer = Peer(
                nodeId  = MTRP.BROADCAST_NODE_ID,
                channel = channel
            )
            runCatching { transport.send(beacon, peer) }
        }
    }

    /**
     * Process a received beacon from another node.
     * Updates the NeighbourTable with the sender's information.
     */
    fun onBeaconReceived(
        senderNodeId: String,
        channel:      ChannelType,
        rssi:         Int,
        transports:   List<ChannelType>,
        platform:     String,
        batteryPct:   Int?
    ) {
        val entry = NeighbourEntry(
            nodeId       = senderNodeId,
            channel      = channel,
            rssi         = rssi,
            lastBeaconMs = currentTimeMs(),
            transports   = transports,
            platform     = platform,
            batteryPct   = batteryPct
        )
        neighbourTable.upsert(entry)
    }

    private fun buildBeaconPacket(activeChannels: List<ChannelType>): MtrpPacket {
        // Beacon uses broadcast routing ID (all 0xFF)
        val routingId = PacketCodec.BROADCAST_ROUTING_ID

        // Payload encodes this node's available transports as a bitmask
        val transportBits = activeChannels.fold(0) { acc, ch -> acc or (1 shl ch.ordinal) }
        val payload = byteArrayOf(
            transportBits.toByte(),
            (transportBits shr 8).toByte()
        )

        return MtrpPacket(
            routingId     = routingId,
            chanType      = ChanType.WIFI,
            relayMac      = ByteArray(32),
            payload       = payload,
            senderSig     = ByteArray(64),
            msgId         = PacketCodec.generateMsgId(),
            ttl           = 1u,
            createdAtMs   = currentTimeMs()
        )
    }
}
