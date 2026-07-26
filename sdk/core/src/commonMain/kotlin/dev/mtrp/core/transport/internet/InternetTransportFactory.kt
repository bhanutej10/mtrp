package dev.mtrp.core.transport.internet

import dev.mtrp.core.ChannelType
import dev.mtrp.core.routing.MeshRouter

/**
 * Registers all internet transports with the MeshRouter.
 * Called once during SDK initialisation.
 *
 * Author: K. Bhanutej
 */
object InternetTransportFactory {

    fun register(
        router:      MeshRouter,
        localNodeId: String,
        serverUrl:   String,
        nostrRelayUrl: String
    ) {
        router.registerTransport(
            ChannelType.WIFI,
            WifiTransport(serverUrl, localNodeId, ChannelType.WIFI)
        )
        router.registerTransport(
            ChannelType.CELLULAR,
            CellularTransport(serverUrl, localNodeId)
        )
        router.registerTransport(
            ChannelType.NOSTR,
            NostrTransport(nostrRelayUrl, localNodeId)
        )
    }
}
