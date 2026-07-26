package dev.mtrp.core.transport

import dev.mtrp.core.ChannelType
import dev.mtrp.core.packet.MtrpPacket
import dev.mtrp.core.routing.TransportStub
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * MTRP-SPEC-v0.1 Section 4.5 — Transport Interface Contract
 *
 * Every transport implementation must satisfy this interface.
 * Real implementations are registered with MeshRouter via registerTransport().
 *
 * Author: K. Bhanutej
 */
enum class TransportStatus { IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR }

data class Peer(
    val nodeId:    String,
    val channel:   ChannelType,
    val rssi:      Int = 0,
    val address:   String = ""
)

interface Transport : TransportStub {
    val type:            ChannelType
    val maxPayloadBytes: Int
    val relayAllowed:    Boolean
    val status:          StateFlow<TransportStatus>
    val peers:           StateFlow<List<Peer>>
    val incoming:        Flow<MtrpPacket>

    suspend fun start()
    suspend fun stop()
    suspend fun send(packet: MtrpPacket, to: Peer): Result<Unit>

    fun signalStrength(): Int
}
