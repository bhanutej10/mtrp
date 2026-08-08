package dev.mtrp.core.transport.lora

import dev.mtrp.core.ChannelType
import dev.mtrp.core.packet.MtrpPacket
import dev.mtrp.core.packet.PacketCodec
import dev.mtrp.core.packet.PacketPadding
import dev.mtrp.core.packet.FragmentAssembler
import dev.mtrp.core.packet.currentTimeMs
import dev.mtrp.core.transport.Peer
import dev.mtrp.core.transport.Transport
import dev.mtrp.core.transport.TransportStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * MTRP-SPEC-v0.1 Section 4.1 — LoRa transport (priority 6)
 *
 * Communicates with a LoRa radio module via a serial port.
 * On Android: USB serial via /dev/ttyUSB0 or /dev/ttyACM0
 * On desktop: /dev/ttyUSB0 (Linux) or COM3 (Windows)
 *
 * LoRa max payload is 50 bytes per the spec. Larger packets
 * are fragmented by FragmentAssembler before transmission.
 * Each fragment is padded to exactly 50 bytes.
 *
 * The gateway pattern:
 *   A desktop or Android device with a LoRa USB module acts as a
 *   gateway between the MTRP mesh and long-range LoRa radio.
 *   Range: 2-15 km line of sight depending on module and antenna.
 *
 * Wire protocol with LoRa module (UART at 115200 baud):
 *   Send:    "AT+SEND=<hex_bytes>\r\n"
 *   Receive: "+RCV=<hex_bytes>,<rssi>,<snr>\r\n"
 *
 * Author: K. Bhanutej
 */
class LoraTransport(
    private val serialPort: String = defaultSerialPort()
) : Transport {

    override val type:            ChannelType = ChannelType.LORA
    override val maxPayloadBytes: Int         = ChannelType.LORA.maxPayloadBytes
    override val relayAllowed:    Boolean     = ChannelType.LORA.relayAllowed

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _status = MutableStateFlow(TransportStatus.IDLE)
    override val status: StateFlow<TransportStatus> = _status

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    override val peers: StateFlow<List<Peer>> = _peers

    private val incomingChannel = Channel<MtrpPacket>(capacity = 128)
    override val incoming: Flow<MtrpPacket> = incomingChannel.receiveAsFlow()

    private val assembler = FragmentAssembler()

    private var serialInput:  InputStream?  = null
    private var serialOutput: OutputStream? = null
    private var serialFile:   File?         = null

    private var lastSuccessMs  = 0L
    private var recentFailures = 0
    private var totalAttempts  = 0
    private var lastRssi       = -120

    companion object {
        const val LORA_BAUD_RATE = 115200
        const val LORA_MAX_BYTES = 50

        fun defaultSerialPort(): String = when {
            System.getProperty("os.name")?.contains("Windows", ignoreCase = true) == true -> "COM3"
            else -> "/dev/ttyUSB0"
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────

    override suspend fun start() {
        try {
            val file = File(serialPort)
            if (!file.exists()) {
                _status.value = TransportStatus.ERROR
                return
            }
            serialFile   = file
            serialInput  = file.inputStream()
            serialOutput = file.outputStream()
            _status.value = TransportStatus.CONNECTED
            startReceiveLoop()
        } catch (e: Exception) {
            _status.value = TransportStatus.ERROR
        }
    }

    override suspend fun stop() {
        runCatching { serialInput?.close() }
        runCatching { serialOutput?.close() }
        serialInput  = null
        serialOutput = null
        _status.value = TransportStatus.IDLE
    }

    // ── Send ──────────────────────────────────────────────────────

    override suspend fun send(packet: MtrpPacket, to: Peer): Result<Unit> {
        totalAttempts++
        val out = serialOutput
            ?: return Result.failure(IllegalStateException("Serial port not open"))

        return try {
            require(packet.payload.size <= LORA_MAX_BYTES) {
                "LoRa payload ${packet.payload.size} bytes exceeds max $LORA_MAX_BYTES — fragment first"
            }

            // Pad to exactly 50 bytes
            val padded = if (packet.payload.size < LORA_MAX_BYTES) {
                val loraPad = PacketPadding.loraFragmentPadding(packet.payload.size)
                packet.copy(pad = loraPad)
            } else packet

            val bytes  = PacketCodec.encode(padded)
            val hex    = bytes.joinToString("") { "%02X".format(it) }
            val cmd    = "AT+SEND=$hex\r\n"

            out.write(cmd.toByteArray())
            out.flush()

            // Minimum inter-packet delay for LoRa duty cycling
            delay(100L)

            lastSuccessMs = currentTimeMs()
            Result.success(Unit)
        } catch (e: Exception) {
            recentFailures++
            Result.failure(e)
        }
    }

    // ── Receive loop ──────────────────────────────────────────────

    private fun startReceiveLoop() {
        scope.launch {
            val input = serialInput ?: return@launch
            val buffer = StringBuilder()

            while (isActive) {
                try {
                    val byte = input.read()
                    if (byte == -1) break
                    val char = byte.toChar()
                    buffer.append(char)

                    if (buffer.endsWith("\r\n")) {
                        val line = buffer.toString().trim()
                        buffer.clear()
                        if (line.startsWith("+RCV=")) {
                            handleReceivedLine(line)
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) _status.value = TransportStatus.ERROR
                    break
                }
            }
        }
    }

    /**
     * Parse incoming LoRa line: +RCV=<hex>,<rssi>,<snr>
     */
    private suspend fun handleReceivedLine(line: String) {
        try {
            // Format: +RCV=AABBCC...,<rssi>,<snr>
            val content = line.removePrefix("+RCV=")
            val parts   = content.split(",")
            if (parts.size < 3) return

            val hexData = parts[0]
            lastRssi    = parts[1].trim().toIntOrNull() ?: -120

            val bytes  = hexToBytes(hexData) ?: return
            val packet = PacketCodec.decode(bytes) ?: return

            // If fragmented, reassemble
            if (packet.isFragmented) {
                val assembled = assembler.addFragment(packet, "lora_peer")
                if (assembled != null) {
                    val full = packet.copy(
                        payload       = assembled,
                        fragmentIndex = 0,
                        fragmentTotal = 1
                    )
                    incomingChannel.send(full)
                }
            } else {
                incomingChannel.send(packet)
            }
        } catch (e: Exception) {
            // Malformed data — discard silently
        }
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (e: Exception) { null }
    }

    override fun isAvailable(): Boolean =
        _status.value == TransportStatus.CONNECTED && serialOutput != null

    override fun estimatedLatencyMs(): Long = ChannelType.LORA.typicalLatencyMs
    override fun signalStrength():     Int  = lastRssi

    override fun recentFailureRate(): Float =
        if (totalAttempts == 0) 0f else recentFailures.toFloat() / totalAttempts

    override fun avgRetryCount():      Float = 0f
    override fun msSinceLastSuccess(): Long  =
        if (lastSuccessMs == 0L) Long.MAX_VALUE else currentTimeMs() - lastSuccessMs
}
