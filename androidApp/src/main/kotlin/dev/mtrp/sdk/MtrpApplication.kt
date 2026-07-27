package dev.mtrp.sdk

import android.app.Application
import dev.mtrp.core.MtrpSdk

class MtrpApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        MtrpSdk.init(
            context = this,
            localNodeId = "your_node_id_here",
            serverUrl = "ws://your-server.com/mtrp",
            nostrRelayUrl = "wss://relay.damus.io"
        )
    }
}
