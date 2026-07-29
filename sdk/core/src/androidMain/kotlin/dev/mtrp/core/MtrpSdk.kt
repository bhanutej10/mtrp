package dev.mtrp.core

import android.content.Context
import dev.mtrp.core.routing.MeshRouter
import dev.mtrp.core.transport.ble.BleTransport
import dev.mtrp.core.transport.internet.InternetTransportFactory
import dev.mtrp.core.transport.sms.SmsTransport

/**
 * SDK entry point. Call MtrpSdk.init() once in Application.onCreate().
 * Author: K. Bhanutej
 */
object MtrpSdk {

    lateinit var router: MeshRouter
        private set

    fun init(
        context:       Context,
        localNodeId:   String,
        serverUrl:     String,
        nostrRelayUrl: String
    ) {
        router = MeshRouter(localNodeId)

        // Register internet transports (WiFi, Cellular, Nostr)
        InternetTransportFactory.register(router, localNodeId, serverUrl, nostrRelayUrl)

        // Register BLE transport
        router.registerTransport(ChannelType.BLE, BleTransport(context))
        router.registerTransport(ChannelType.SMS, SmsTransport(context))
    }
}
