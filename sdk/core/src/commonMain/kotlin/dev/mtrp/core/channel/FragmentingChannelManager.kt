package dev.mtrp.core.channel

import dev.mtrp.core.ChannelType
import dev.mtrp.core.packet.FragmentAssembler
import dev.mtrp.core.packet.MtrpPacket
import dev.mtrp.core.packet.PacketPadding
import dev.mtrp.core.routing.MeshRouter
import dev.mtrp.core.transport.Peer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Extends ChannelManager with automatic fragmentation.
 *
 * When a packet payload exceeds the selected channel's maxPayloadBytes,
 * it is fragmented by FragmentAssembler before sending. Each fragment
 * is padded to the nearest bucket size and sent individually.
 *
 * On receive, fragments are reassembled transparently before the packet
 * reaches the application layer.
 *
 * This is mandatory for BLE (512 bytes) and LoRa (50 bytes).
 *
 * Author: K. Bhanutej
 */
class FragmentingChannelManager(router: MeshRouter) : ChannelManager(router) {

    private val assembler = FragmentAssembler()

    private val _reassembled = MutableSharedFlow<MtrpPacket>(
        replay = 0, extraBufferCapacity = 64
    )

    val reassembled: Flow<MtrpPacket> = _reassembled

    /**
     * Send a packet, fragmenting automatically if needed for the selected channel.
     * Returns the channel used, or null if no channel was available.
     */
    suspend fun sendFragmented(
        packet:     MtrpPacket,
        destPeer:   Peer,
        batteryPct: Int,
        isRelay:    Boolean = false
    ): ChannelType? {
        val channel   = if (isRelay) bestForRelay(packet, batteryPct)
                        else         bestForSend(packet, batteryPct)
        val transport = channel?.let { this.transport(it) } ?: return null

        val maxBytes  = channel.maxPayloadBytes

        val fragments = assembler.fragment(
            payload  = packet.payload,
            template = packet,
            maxChunkSize = maxBytes
        )

        var allSuccess = true
        for (fragment in fragments) {
            val padded = PacketPadding.applyPadding(fragment)
            val result = transport.send(padded, destPeer)
            if (result.isFailure) {
                allSuccess = false
                recordFailure(channel)
                break
            }
            recordSuccess(channel, channel.typicalLatencyMs)
        }

        return if (allSuccess) channel else null
    }

    /**
     * Add a received fragment. Returns the reassembled packet if all
     * fragments have arrived, or null if more are expected.
     */
    suspend fun receiveFragment(packet: MtrpPacket, originId: String): MtrpPacket? {
        val assembled = assembler.addFragment(packet, originId)
        return if (assembled != null) {
            val full = packet.copy(
                payload       = assembled,
                fragmentIndex = 0,
                fragmentTotal = 1
            )
            _reassembled.emit(full)
            full
        } else null
    }
}
