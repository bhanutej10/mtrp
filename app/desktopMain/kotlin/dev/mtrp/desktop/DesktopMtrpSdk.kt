package dev.mtrp.desktop

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.mtrp.core.ChannelType
import dev.mtrp.core.api.MtrpApi
import dev.mtrp.core.channel.FragmentingChannelManager
import dev.mtrp.core.crypto.IdentityStore
import dev.mtrp.core.crypto.NodeIdentity
import dev.mtrp.core.crypto.loadOrGenerate
import dev.mtrp.core.db.MtrpDatabase
import dev.mtrp.core.queue.QueuedTransport
import dev.mtrp.core.queue.RetryScheduler
import dev.mtrp.core.queue.StoreForwardQueue
import dev.mtrp.core.routing.MeshRouter
import dev.mtrp.core.transport.ethernet.EthernetTransport
import dev.mtrp.core.transport.internet.CellularTransport
import dev.mtrp.core.transport.internet.NostrTransport
import dev.mtrp.core.transport.internet.WifiTransport
import dev.mtrp.core.transport.lora.LoraTransport
import java.io.File
import java.util.Properties
import com.ionspin.kotlin.crypto.LibsodiumInitializer

/**
 * Desktop SDK entry point.
 * Equivalent to MtrpSdk.kt on Android but for Linux and Windows.
 * Registers only the channels available on desktop.
 *
 * Available channels on desktop:
 *   WiFi (internet), Nostr (internet), Ethernet (LAN),
 *   LoRa (USB serial), Store and Forward
 *
 * Not available on desktop:
 *   BLE, SMS, WiFi Direct, Cellular (Android-only hardware APIs)
 *
 * Author: K. Bhanutej
 */
object DesktopMtrpSdk {

    lateinit var api:    MtrpApi
        private set
    lateinit var router: MeshRouter
        private set

    private var retryScheduler: RetryScheduler? = null

    suspend fun init(
        serverUrl:     String = "ws://localhost:8080/mtrp",
        nostrRelayUrl: String = "wss://relay.damus.io",
        dbPath:        String = "${userDataDir()}/mtrp.db",
        configPath:    String = "${userDataDir()}/mtrp.properties"
    ) {
    
    	com.ionspin.kotlin.crypto.LibsodiumInitializer.initializeWithCallback {}
    	
        // Load or generate node identity
        val store    = DesktopIdentityStore(configPath)
        val identity = store.loadOrGenerate()

        // Set up routing
        router = MeshRouter(identity.nodeId)

        // Set up channel manager
        val channelManager = FragmentingChannelManager(router)

        // Register desktop-available transports
        channelManager.register(WifiTransport(serverUrl,     identity.nodeId, ChannelType.WIFI))
        channelManager.register(NostrTransport(nostrRelayUrl, identity.nodeId))
        channelManager.register(EthernetTransport())

        // LoRa — register only if USB module is present
        val loraPort = detectLoraPort()
        if (loraPort != null) {
            channelManager.register(LoraTransport(loraPort))
        }

        // Store and forward queue
        val driver   = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
        MtrpDatabase.Schema.create(driver)
        val database = MtrpDatabase(driver)
        val queue    = StoreForwardQueue(database)
        channelManager.register(QueuedTransport(queue, identity.nodeId))

        // Retry scheduler
        retryScheduler = RetryScheduler(queue, router)
        retryScheduler?.start()

        // Start all transports
        channelManager.startAll()

        // Build public API
        api = MtrpApi(identity, channelManager, router, queue)
    }

    fun stop() {
        retryScheduler?.stop()
    }

    private fun detectLoraPort(): String? = listOf(
        "/dev/ttyUSB0", "/dev/ttyUSB1",
        "/dev/ttyACM0", "/dev/ttyACM1",
        "COM3", "COM4", "COM5"
    ).firstOrNull { File(it).exists() }

    private fun userDataDir(): String {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win")   -> "${System.getenv("APPDATA")}/MTRP"
            os.contains("mac")   -> "${System.getProperty("user.home")}/Library/Application Support/MTRP"
            else                 -> "${System.getProperty("user.home")}/.config/mtrp"
        }.also { File(it).mkdirs() }
    }
}

/**
 * Desktop identity store using a properties file.
 * Stores Ed25519 keypair as Base64 in ~/.config/mtrp/mtrp.properties
 * on Linux, %APPDATA%/MTRP/mtrp.properties on Windows.
 */
class DesktopIdentityStore(private val path: String) : IdentityStore {

    private val file = File(path)

    override suspend fun load(): NodeIdentity? {
        if (!file.exists()) return null
        return try {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            val pubB64  = props.getProperty("pub_key")  ?: return null
            val privB64 = props.getProperty("priv_key") ?: return null
            val pub  = java.util.Base64.getDecoder().decode(pubB64)
            val priv = java.util.Base64.getDecoder().decode(privB64)
            NodeIdentity.fromStoredKeys(pub, priv)
        } catch (e: Exception) { null }
    }

    override suspend fun save(identity: NodeIdentity) {
        file.parentFile?.mkdirs()
        val props = Properties()
        props.setProperty("pub_key",  java.util.Base64.getEncoder().encodeToString(identity.publicKey))
        props.setProperty("priv_key", java.util.Base64.getEncoder().encodeToString(identity.privateKey))
        file.outputStream().use {
            props.store(it, "MTRP node identity — do not share or edit")
        }
    }

    override suspend fun clear() { file.delete() }

    override fun isHardwareBacked() = false
}
