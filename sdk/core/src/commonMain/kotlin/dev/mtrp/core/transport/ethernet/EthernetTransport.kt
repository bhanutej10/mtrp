package dev.mtrp.core.transport.ethernet

import dev.mtrp.core.ChannelType
import dev.mtrp.core.packet.MtrpPacket
import dev.mtrp.core.packet.PacketCodec
import dev.mtrp.core.packet.currentTimeMs
import dev.mtrp.core.transport.Peer
import dev.mtrp.core.transport.Transport
import dev.mtrp.core.transport.TransportStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/**
 * MTRP Ethernet transport — wired LAN via TCP sockets.
 *
 * Works on any device with a network interface — desktop Linux,
 * desktop Windows, and Android devices with USB Ethernet adapters.
 * No internet required. Operates entirely on the local LAN.
 *
 * Architecture:
 *   Each node runs a ServerSocket on port 8766.
 *   On start, the node scans the local subnet and attempts to connect
 *   to port 8766 on every reachable host. Any host running MTRP
 *   accepts the connection and becomes a peer.
 *
 * Packet framing: 4-byte big-endian length prefix followed by packet bytes.
 * Same framing as WifiDirectTransport for consistency.
 *
 * Sub-millisecond latency on gigabit LAN.
 * No hardware pairing or discovery protocol required.
 *
 * Author: K. Bhanutej
 */
class EthernetTransport(
    private val listenPort: Int = MTRP_ETHERNET_PORT
) : Transport {

    override val type:            ChannelType = ChannelType.ETHERNET
    override val maxPayloadBytes: Int         = ChannelType.ETHERNET.maxPayloadBytes
    override val relayAllowed:    Boolean     = ChannelType.ETHERNET.relayAllowed

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _status = MutableStateFlow(TransportStatus.IDLE)
    override val status: StateFlow<TransportStatus> = _status

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    override val peers: StateFlow<List<Peer>> = _peers

    private val incomingChannel = Channel<MtrpPacket>(capacity = 256)
    override val incoming: Flow<MtrpPacket> = incomingChannel.receiveAsFlow()

    private val connectedSockets = mutableMapOf<String, Socket>()
    private var serverSocket: ServerSocket? = null

    private var lastSuccessMs  = 0L
    private var recentFailures = 0
    private var totalAttempts  = 0

    companion object {
        const val MTRP_ETHERNET_PORT = 8766
    }

    // ── Lifecycle ─────────────────────────────────────────────────

    override suspend fun start() {
        startServer()
        scanAndConnect()
        _status.value = TransportStatus.CONNECTED
    }

    override suspend fun stop() {
        serverSocket?.close()
        connectedSockets.values.forEach { runCatching { it.close() } }
        connectedSockets.clear()
        _status.value = TransportStatus.IDLE
    }

    // ── Send ──────────────────────────────────────────────────────

    override suspend fun send(packet: MtrpPacket, to: Peer): Result<Unit> {
        totalAttempts++
        return try {
            val socket = connectedSockets[to.address]
                ?: return Result.failure(IllegalStateException("Not connected to ${to.address}"))
            val bytes = PacketCodec.encode(packet)
            writeFramed(socket.getOutputStream(), bytes)
            lastSuccessMs = currentTimeMs()
            Result.success(Unit)
        } catch (e: Exception) {
            recentFailures++
            connectedSockets.remove(to.address)
            updatePeers()
            Result.failure(e)
        }
    }

    /**
     * Broadcast a packet to all connected peers on the LAN.
     * Used for beacon broadcasts and RREQ floods.
     */
    suspend fun broadcast(packet: MtrpPacket): Int {
        val bytes = PacketCodec.encode(packet)
        var sent = 0
        val dead = mutableListOf<String>()
        connectedSockets.forEach { (address, socket) ->
            try {
                writeFramed(socket.getOutputStream(), bytes)
                sent++
            } catch (e: Exception) {
                dead.add(address)
            }
        }
        dead.forEach { connectedSockets.remove(it) }
        if (dead.isNotEmpty()) updatePeers()
        return sent
    }

    // ── Server ────────────────────────────────────────────────────

    private fun startServer() {
        scope.launch {
            val server = ServerSocket(listenPort)
            serverSocket = server
            while (!server.isClosed) {
                try {
                    val client  = server.accept()
                    val address = client.inetAddress.hostAddress ?: continue
                    connectedSockets[address] = client
                    updatePeers()
                    scope.launch { readLoop(client, address) }
                } catch (e: Exception) {
                    if (!server.isClosed) _status.value = TransportStatus.ERROR
                }
            }
        }
    }

    // ── Subnet scan ───────────────────────────────────────────────

    /**
     * Scans all addresses on the local subnet and attempts to connect
     * to any that have port 8766 open. Runs concurrently for speed.
     */
    private fun scanAndConnect() {
        scope.launch {
            val localAddresses = localIpAddresses()
            localAddresses.forEach { localIp ->
                val subnet = localIp.substringBeforeLast(".")
                (1..254).forEach { host ->
                    val target = "$subnet.$host"
                    if (target != localIp && !connectedSockets.containsKey(target)) {
                        scope.launch { tryConnect(target) }
                    }
                }
            }
        }
    }

    private fun tryConnect(address: String) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(address, listenPort), 200)
            connectedSockets[address] = socket
            updatePeers()
            scope.launch { readLoop(socket, address) }
        } catch (e: Exception) {
            // Host not reachable or not running MTRP — skip silently
        }
    }

    // ── Read loop ─────────────────────────────────────────────────

    private suspend fun readLoop(socket: Socket, address: String) {
        try {
            val input = socket.getInputStream()
            while (!socket.isClosed) {
                val bytes  = readFramed(input) ?: break
                val packet = PacketCodec.decode(bytes) ?: continue
                incomingChannel.send(packet)
            }
        } catch (e: Exception) {
            // Connection closed
        } finally {
            connectedSockets.remove(address)
            updatePeers()
        }
    }

    // ── Framing ───────────────────────────────────────────────────

    private fun writeFramed(out: OutputStream, bytes: ByteArray) {
        val len = bytes.size
        out.write(byteArrayOf(
            (len shr 24).toByte(),
            (len shr 16).toByte(),
            (len shr 8).toByte(),
            len.toByte()
        ))
        out.write(bytes)
        out.flush()
    }

    private fun readFramed(input: InputStream): ByteArray? {
        val lenBytes = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = input.read(lenBytes, read, 4 - read)
            if (n < 0) return null
            read += n
        }
        val len = ((lenBytes[0].toInt() and 0xFF) shl 24) or
                  ((lenBytes[1].toInt() and 0xFF) shl 16) or
                  ((lenBytes[2].toInt() and 0xFF) shl 8)  or
                   (lenBytes[3].toInt() and 0xFF)
        if (len <= 0 || len > maxPayloadBytes + 200) return null
        val buf = ByteArray(len)
        var received = 0
        while (received < len) {
            val n = input.read(buf, received, len - received)
            if (n < 0) return null
            received += n
        }
        return buf
    }

    // ── Utilities ─────────────────────────────────────────────────

    private fun localIpAddresses(): List<String> {
        return try {
            NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
                .map { it.hostAddress ?: "" }
                .filter { it.isNotEmpty() }
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun updatePeers() {
        _peers.value = connectedSockets.keys.map { address ->
            Peer(nodeId = address, channel = ChannelType.ETHERNET, address = address)
        }
    }

    override fun isAvailable(): Boolean =
        _status.value == TransportStatus.CONNECTED

    override fun estimatedLatencyMs(): Long = ChannelType.ETHERNET.typicalLatencyMs
    override fun signalStrength():     Int  = 0

    override fun recentFailureRate(): Float =
        if (totalAttempts == 0) 0f else recentFailures.toFloat() / totalAttempts

    override fun avgRetryCount():      Float = 0f
    override fun msSinceLastSuccess(): Long  =
        if (lastSuccessMs == 0L) Long.MAX_VALUE else currentTimeMs() - lastSuccessMs
}
