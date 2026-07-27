package dev.mtrp.core.transport.ble


import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.annotation.SuppressLint
import androidx.core.content.ContextCompat
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import dev.mtrp.core.ChannelType
import dev.mtrp.core.MTRP
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
import java.util.UUID

/**
 * MTRP-SPEC-v0.1 Section 4.1 — BLE transport (priority 4)
 *
 * Operates as both a GATT server (receives packets) and GATT client
 * (sends packets to discovered peers). Uses passive scanning at
 * SCAN_MODE_LOW_POWER as required by the spec.
 *
 * BLE MAC address rotation every 15 minutes is handled by Android
 * automatically when using the BLE advertiser on Android 8+.
 *
 * Max payload: 512 bytes per the spec. Large packets must be
 * fragmented by FragmentAssembler before reaching this transport.
 *
 * Author: K. Bhanutej
 */
 @SuppressLint("MissingPermission")
class BleTransport(private val context: Context) : Transport {

    override val type:            ChannelType = ChannelType.BLE
    override val maxPayloadBytes: Int         = ChannelType.BLE.maxPayloadBytes
    override val relayAllowed:    Boolean     = ChannelType.BLE.relayAllowed

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _status = MutableStateFlow(TransportStatus.IDLE)
    override val status: StateFlow<TransportStatus> = _status

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    override val peers: StateFlow<List<Peer>> = _peers

    private val incomingChannel = Channel<MtrpPacket>(capacity = 256)
    override val incoming: Flow<MtrpPacket> = incomingChannel.receiveAsFlow()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var gattServer:  BluetoothGattServer? = null
    private var advertiser   = bluetoothAdapter?.bluetoothLeAdvertiser
    private var scanner      = bluetoothAdapter?.bluetoothLeScanner

    private var lastSuccessMs  = 0L
    private var recentFailures = 0
    private var totalAttempts  = 0

    private val connectedGatts = mutableMapOf<String, BluetoothGatt>()

    companion object {
        val SERVICE_UUID        = UUID.fromString("0000BEEF-0000-1000-8000-00805F9B34FB")
        val PACKET_CHAR_UUID    = UUID.fromString("0000BEF0-0000-1000-8000-00805F9B34FB")
        val MAX_MTU             = 512
    }

	private fun hasPermission(permission: String): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

private fun hasScanPermission(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        hasPermission(Manifest.permission.BLUETOOTH_SCAN)
}

private fun hasConnectPermission(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
}

private fun hasAdvertisePermission(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
}
	
    override suspend fun start() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _status.value = TransportStatus.ERROR
            return
        }
        startGattServer()
        startAdvertising()
        startScanning()
        _status.value = TransportStatus.CONNECTED
    }

    override suspend fun stop() {
        advertiser?.stopAdvertising(advertiseCallback)
        scanner?.stopScan(scanCallback)
        gattServer?.close()
        connectedGatts.values.forEach { it.close() }
        connectedGatts.clear()
        _status.value = TransportStatus.IDLE
    }

    override suspend fun send(packet: MtrpPacket, to: Peer): Result<Unit> {
        totalAttempts++
        return try {
            require(packet.payload.size <= maxPayloadBytes) {
                "Payload ${packet.payload.size} bytes exceeds BLE max $maxPayloadBytes"
            }
            val gatt = connectedGatts[to.address]
                ?: return Result.failure(IllegalStateException("Not connected to peer ${to.nodeId}"))
            val service = gatt.getService(SERVICE_UUID)
                ?: return Result.failure(IllegalStateException("MTRP service not found"))
            val characteristic = service.getCharacteristic(PACKET_CHAR_UUID)
                ?: return Result.failure(IllegalStateException("Packet characteristic not found"))

            val bytes = PacketCodec.encode(packet)
            characteristic.value = bytes
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gatt.writeCharacteristic(characteristic)

            lastSuccessMs = currentTimeMs()
            Result.success(Unit)
        } catch (e: Exception) {
            recentFailures++
            Result.failure(e)
        }
    }

    // ── GATT Server ──────────────────────────────────────────────────

    private fun startGattServer() {
        val service = BluetoothGattService(
            SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        val characteristic = BluetoothGattCharacteristic(
            PACKET_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(characteristic)

        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        gattServer?.addService(service)
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onCharacteristicWriteRequest(
            device: android.bluetooth.BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            val packet = PacketCodec.decode(value)
            if (packet != null) {
                scope.launch { incomingChannel.send(packet) }
            }
        }

        override fun onConnectionStateChange(
            device: android.bluetooth.BluetoothDevice,
            status: Int,
            newState: Int
        ) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                updatePeers()
            }
        }
    }

    // ── Advertising ──────────────────────────────────────────────────

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {}
        override fun onStartFailure(errorCode: Int) {
            _status.value = TransportStatus.ERROR
        }
    }

    // ── Scanning ─────────────────────────────────────────────────────

    private fun startScanning() {
        // SPEC: MUST use SCAN_MODE_LOW_POWER — passive scanning only
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        scanner?.startScan(null, settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device    = result.device
            val uuids     = result.scanRecord?.serviceUuids ?: return
            val isMtrp    = uuids.any { it.uuid == SERVICE_UUID }
            if (!isMtrp) return

            // Connect to newly discovered MTRP peer
            if (!connectedGatts.containsKey(device.address)) {
                device.connectGatt(context, false, gattClientCallback)
            }

            updatePeers()
        }
    }

    // ── GATT Client ──────────────────────────────────────────────────

    private val gattClientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedGatts[gatt.device.address] = gatt
                    gatt.requestMtu(MAX_MTU)
                    gatt.discoverServices()
                    updatePeers()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedGatts.remove(gatt.device.address)
                    gatt.close()
                    updatePeers()
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val packet = PacketCodec.decode(characteristic.value)
            if (packet != null) {
                scope.launch { incomingChannel.send(packet) }
            }
        }
    }

    private fun updatePeers() {
        _peers.value = connectedGatts.keys.map { address ->
            Peer(nodeId = address, channel = ChannelType.BLE, address = address)
        }
    }

    override fun isAvailable(): Boolean =
        _status.value == TransportStatus.CONNECTED && bluetoothAdapter?.isEnabled == true

    override fun estimatedLatencyMs(): Long = ChannelType.BLE.typicalLatencyMs
    override fun signalStrength():     Int  = 0

    override fun recentFailureRate(): Float =
        if (totalAttempts == 0) 0f else recentFailures.toFloat() / totalAttempts

    override fun avgRetryCount():      Float = 0f
    override fun msSinceLastSuccess(): Long  =
        if (lastSuccessMs == 0L) Long.MAX_VALUE else currentTimeMs() - lastSuccessMs
}
