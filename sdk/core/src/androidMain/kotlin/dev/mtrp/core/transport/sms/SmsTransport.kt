package dev.mtrp.core.transport.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.SmsManager
import android.telephony.SmsMessage
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
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * MTRP-SPEC-v0.1 Section 4.1 — SMS transport (priority 5)
 *
 * SMS uses the 2G signalling channel — not the data channel.
 * Works when the device has no internet but still has 2G signal.
 *
 * Critical spec constraint: relayAllowed = false
 * SMS MUST only be used by the origin node of a packet.
 * Relay nodes MUST NOT use this transport to forward packets.
 * This is enforced at the ChannelType level (relayAllowed=false)
 * and additionally checked in bestForRelay() in MeshRouter.
 *
 * Max payload: 140 bytes per SMS. Packets larger than 140 bytes
 * must be fragmented before reaching this transport.
 *
 * Encoding: packet bytes are Base64-encoded before sending
 * since SMS carries text. A 140-byte raw packet becomes
 * approximately 188 Base64 characters — sent as a multipart SMS.
 *
 * Author: K. Bhanutej
 */
@OptIn(ExperimentalEncodingApi::class)
class SmsTransport(private val context: Context) : Transport {

    override val type:            ChannelType = ChannelType.SMS
    override val maxPayloadBytes: Int         = ChannelType.SMS.maxPayloadBytes
    override val relayAllowed:    Boolean     = false   // hard override — SPEC 4.1

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _status = MutableStateFlow(TransportStatus.IDLE)
    override val status: StateFlow<TransportStatus> = _status

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    override val peers: StateFlow<List<Peer>> = _peers

    private val incomingChannel = Channel<MtrpPacket>(capacity = 64)
    override val incoming: Flow<MtrpPacket> = incomingChannel.receiveAsFlow()

    private var lastSuccessMs  = 0L
    private var recentFailures = 0
    private var totalAttempts  = 0

    // Prefix added to every outgoing SMS so receivers identify it as MTRP
    private val MTRP_SMS_PREFIX = "MTRP:"

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return
            val bundle = intent.extras ?: return
            val pdus   = bundle.get("pdus") as? Array<*> ?: return
            val format = bundle.getString("format")

            val fullText = pdus.joinToString("") { pdu ->
                SmsMessage.createFromPdu(pdu as ByteArray, format).messageBody ?: ""
            }

            if (!fullText.startsWith(MTRP_SMS_PREFIX)) return

            scope.launch {
                try {
                    val b64   = fullText.removePrefix(MTRP_SMS_PREFIX)
                    val bytes = Base64.decode(b64)
                    val packet = PacketCodec.decode(bytes)
                    if (packet != null) incomingChannel.send(packet)
                } catch (e: Exception) {
                    // Malformed SMS — discard silently
                }
            }
        }
    }

    override suspend fun start() {
        val filter = IntentFilter("android.provider.Telephony.SMS_RECEIVED")
        context.registerReceiver(smsReceiver, filter)
        _status.value = TransportStatus.CONNECTED
    }

    override suspend fun stop() {
        try {
            context.unregisterReceiver(smsReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
        _status.value = TransportStatus.IDLE
    }

    override suspend fun send(packet: MtrpPacket, to: Peer): Result<Unit> {
        // SPEC 4.1: relay nodes must never call this — enforced by relayAllowed=false
        // and MeshRouter.bestForRelay() filtering. Double-checked here defensively.
        if (packet.ttl < 10u) {
            // TTL has been decremented — this is a relay attempt, not origin send
            return Result.failure(
                IllegalStateException("SMS MUST NOT be used for relay — SPEC 4.1")
            )
        }

        totalAttempts++
        return try {
            val bytes  = PacketCodec.encode(packet)
            val b64    = Base64.encode(bytes)
            val text   = "$MTRP_SMS_PREFIX$b64"
            val phone  = to.address

            require(phone.isNotEmpty()) { "Peer phone number is required for SMS transport" }

            val smsManager = context.getSystemService(SmsManager::class.java)
            val parts      = smsManager.divideMessage(text)

            if (parts.size == 1) {
                smsManager.sendTextMessage(phone, null, text, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            }

            lastSuccessMs = currentTimeMs()
            Result.success(Unit)
        } catch (e: Exception) {
            recentFailures++
            Result.failure(e)
        }
    }

    override fun isAvailable(): Boolean = _status.value == TransportStatus.CONNECTED

    override fun estimatedLatencyMs(): Long = ChannelType.SMS.typicalLatencyMs
    override fun signalStrength():     Int  = 0

    override fun recentFailureRate(): Float =
        if (totalAttempts == 0) 0f else recentFailures.toFloat() / totalAttempts

    override fun avgRetryCount():      Float = 0f
    override fun msSinceLastSuccess(): Long  =
        if (lastSuccessMs == 0L) Long.MAX_VALUE else currentTimeMs() - lastSuccessMs
}
