package dev.mtrp.sdk

import android.app.Application
import dev.mtrp.core.MtrpSdk

class MtrpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val driver = AndroidSqliteDriver(
            schema   = MtrpDatabase.Schema,
            context  = this,
            name     = "mtrp.db",
            factory  = SupportFactory("mtrp_db_passphrase".toByteArray())
        )
        kotlinx.coroutines.MainScope().launch {
            MtrpSdk.init(
                context       = this@MtrpApplication,
                serverUrl     = "ws://your-server.com/mtrp",
                nostrRelayUrl = "wss://relay.damus.io",
                dbDriver      = driver
            )
        }
    }
}
