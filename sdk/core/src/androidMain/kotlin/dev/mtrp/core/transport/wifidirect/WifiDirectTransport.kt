package dev.mtrp.core.transport.wifidirect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ActionListener
import android.net.wifi.p2p.WifiP2pManager.PeerListListener
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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * MTRP-SPEC-v0.1 Section 4.1 — WiFi Direct transport (priority 3)
 *
 * Uses Android WiFi P2P API to form direct device-to-device connections
 * without a router or internet. Range is typically 50-200 metres.
 *
 * Architecture:
 *   Group Owner (GO) — acts as a soft AP, opens a ServerSocket on port 8765
 *   Group Client     — connects to the GO's socket after P2P group formation
 *
 * Packet framing: 4-byte big-endian length prefix followed by packet bytes.
 * This allows multiple packets to be sent over a single TCP connection.
 *
 * Max payload: 65536 bytes. Relay is allowed.
 *
 * Author: K. Bhanutej
 */
class WifiDirectTransport(private val context: Context) : Transport {

    override val type:            ChannelType = ChannelType.WIFI_DIRECT
    override val maxPayloadBytes: Int         = ChannelType.WIFI_DIRECT.maxPayloadBytes
    override val relayAllowed:    Boolean     = ChannelType.WIFI_DIRECT.relayAllowed

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _status = MutableStateFlow(TransportStatus.IDLE)
    override val status: StateFlow<TransportStatus> = _status

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    override val peers: StateFlow<List<Peer>> = _peers

    private val incomingChannel = Channel<MtrpPacket>(capacity = 256)
    override val incoming: Flow<MtrpPacket> = incomingChannel.receiveAsFlow()

    private val wifiP2pManager: WifiP2pManager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel: WifiP2pManager.Channel =
        wifiP2pManager.initialize(context, context.mainLooper, null)

    private val connectedSockets = mutableMapOf<String, Socket>()
    private var serverSocket:     ServerSocket? = null

    private var lastSuccessMs  = 0L
    private var recentFailures = 0
    private var totalAttempts  = 0

    companion object {
        const val MTRP_P2P_PORT = 8765
    }

    // ── Lifecycle ─────────────────────────────────────────────────

    override suspend fun start() {
        registerReceiver()
        discoverPeers()
        startServer()
        _status.value = TransportStatus.CONNECTING
    }

    override suspend fun stop() {
        try { context.unregisterReceiver(p2pReceiver) } catch (e: Exception) {}
        serverSocket?.close()
        connectedSockets.values.forEach { runCatching { it.close() } }
        connectedSockets.clear()
        wifiP2pManager.removeGroup(channel, null)
        _status.value = TransportStatus.IDLE
    }

    // ── Send ──────────────────────────────────────────────────────

    override suspend fun send(packet: MtrpPacket, to: Peer): Result<Unit> {
        totalAttempts++
        return try {
            val socket = connectedSockets[to.address]
                ?: return Result.failure(IllegalStateException("Not connected to ${to.nodeId}"))
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

    // ── Peer discovery ────────────────────────────────────────────

    private fun discoverPeers() {
        wifiP2pManager.discoverPeers(channel, object : ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) {
                _status.value = TransportStatus.ERROR
            }
        })
    }

    private val peerListListener = PeerListListener { peerList ->
        val devices = peerList.deviceList.toList()
        scope.launch {
            devices.forEach { device ->
                if (!connectedSockets.containsKey(device.deviceAddress)) {
                    connectToDevice(device)
                }
            }
        }
    }

    private suspend fun connectToDevice(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }
        suspendCancellableCoroutine<Unit> { cont ->
            wifiP2pManager.connect(channel, config, object : ActionListener {
                override fun onSuccess() { cont.resume(Unit) }
                override fun onFailure(reason: Int) {
                    cont.resumeWithException(Exception("P2P connect failed: $reason"))
                }
            })
        }
    }

    // ── Server socket (Group Owner) ───────────────────────────────

    private fun startServer() {
        scope.launch {
            val server = ServerSocket(MTRP_P2P_PORT)
            serverSocket = server
            _status.value = TransportStatus.CONNECTED
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

    // ── Client socket (Group Client) ──────────────────────────────

    private fun connectAsClient(groupOwnerAddress: String) {
        scope.launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(groupOwnerAddress, MTRP_P2P_PORT), 5000)
                connectedSockets[groupOwnerAddress] = socket
                updatePeers()
                _status.value = TransportStatus.CONNECTED
                readLoop(socket, groupOwnerAddress)
            } catch (e: Exception) {
                _status.value = TransportStatus.ERROR
            }
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

    // ── Framing — 4-byte length prefix ───────────────────────────

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

    // ── BroadcastReceiver ─────────────────────────────────────────

    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    wifiP2pManager.requestPeers(channel, peerListListener)
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(
                        WifiP2pManager.EXTRA_NETWORK_INFO
                    )
                    if (networkInfo?.isConnected == true) {
                        wifiP2pManager.requestConnectionInfo(channel) { info ->
                            if (info.groupFormed) {
                                if (info.isGroupOwner) {
                                    // Already running server — nothing to do
                                } else {
                                    connectAsClient(info.groupOwnerAddress.hostAddress ?: return@requestConnectionInfo)
                                }
                            }
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        _status.value = TransportStatus.ERROR
                    }
                }
            }
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        context.registerReceiver(p2pReceiver, filter)
    }

    private fun updatePeers() {
        _peers.value = connectedSockets.keys.map { addr ->
            Peer(nodeId = addr, channel = ChannelType.WIFI_DIRECT, address = addr)
        }
    }

    override fun isAvailable(): Boolean =
        _status.value == TransportStatus.CONNECTED && connectedSockets.isNotEmpty()

    override fun estimatedLatencyMs(): Long = ChannelType.WIFI_DIRECT.typicalLatencyMs
    override fun signalStrength():     Int  = 0

    override fun recentFailureRate(): Float =
        if (totalAttempts == 0) 0f else recentFailures.toFloat() / totalAttempts

    override fun avgRetryCount():      Float = 0f
    override fun msSinceLastSuccess(): Long  =
        if (lastSuccessMs == 0L) Long.MAX_VALUE else currentTimeMs() - lastSuccessMs
}
