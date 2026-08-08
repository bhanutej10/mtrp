package dev.mtrp.core

import android.content.Context
import dev.mtrp.core.api.MtrpApi
import dev.mtrp.core.channel.ChannelManager
import dev.mtrp.core.crypto.AndroidIdentityStore
import dev.mtrp.core.crypto.loadOrGenerate
import dev.mtrp.core.db.MtrpDatabase
import dev.mtrp.core.queue.QueuedTransport
import dev.mtrp.core.queue.RetryScheduler
import dev.mtrp.core.queue.StoreForwardQueue
import dev.mtrp.core.routing.MeshRouter
import dev.mtrp.core.transport.ble.BleTransport
import dev.mtrp.core.transport.internet.CellularTransport
import dev.mtrp.core.transport.internet.NostrTransport
import dev.mtrp.core.transport.internet.WifiTransport
import dev.mtrp.core.transport.sms.SmsTransport
import dev.mtrp.core.transport.wifidirect.WifiDirectTransport
import dev.mtrp.core.channel.FragmentingChannelManager
import dev.mtrp.core.transport.ethernet.EthernetTransport
import dev.mtrp.core.transport.lora.LoraTransport
import java.io.File



/**
 * MTRP SDK entry point.
 *
 * Call MtrpSdk.init() once in Application.onCreate().
 * Access the API via MtrpSdk.api after init.
 *
 * Author: K. Bhanutej
 */
object MtrpSdk {

    lateinit var api: MtrpApi
        private set

    lateinit var router: MeshRouter
        private set

    private var retryScheduler: RetryScheduler? = null

    /**
     * Initialise the SDK. Must be called before any other SDK method.
     *
     * @param context       Android application context
     * @param serverUrl     WebSocket relay server URL e.g. ws://your-server.com/mtrp
     * @param nostrRelayUrl Nostr relay WebSocket URL e.g. wss://relay.damus.io
     * @param dbDriver      SQLDelight driver — use AndroidSqliteDriver in production
     */
    suspend fun init(
        context:       Context,
        serverUrl:     String,
        nostrRelayUrl: String,
        dbDriver:      app.cash.sqldelight.db.SqlDriver
    ) {
        // Load or generate node identity
        val identityStore = AndroidIdentityStore(context)
        val identity      = identityStore.loadOrGenerate()

        // Set up routing
        router = MeshRouter(identity.nodeId)

        // Set up channel manager
        val channelManager = FragmentingChannelManager(router)

        // Register all transports
        channelManager.register(WifiTransport(serverUrl,    identity.nodeId, ChannelType.WIFI))
        channelManager.register(WifiDirectTransport(context))
        channelManager.register(CellularTransport(serverUrl, identity.nodeId))
        channelManager.register(BleTransport(context))
        channelManager.register(SmsTransport(context))
        channelManager.register(NostrTransport(nostrRelayUrl, identity.nodeId))
        channelManager.register(EthernetTransport())
        

	val loraPort = when {
    	File("/dev/ttyUSB0").exists() -> "/dev/ttyUSB0"
    	File("/dev/ttyACM0").exists() -> "/dev/ttyACM0"
    	else -> null
	}
	if (loraPort != null) {
   	 channelManager.register(LoraTransport(loraPort))
	}
        // Set up store and forward queue
        val database = MtrpDatabase(dbDriver)
        val queue    = StoreForwardQueue(database)
        channelManager.register(QueuedTransport(queue, identity.nodeId))

        // Set up retry scheduler
        retryScheduler = RetryScheduler(queue, router)
        retryScheduler?.start()

        // Build public API
        api = MtrpApi(identity, channelManager, router, queue)
    }

    fun stop() {
        retryScheduler?.stop()
    }
}
