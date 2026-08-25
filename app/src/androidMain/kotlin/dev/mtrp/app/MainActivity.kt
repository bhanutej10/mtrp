package dev.mtrp.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import dev.mtrp.app.data.ChatMessage
import dev.mtrp.app.data.Contact
import dev.mtrp.app.data.ContactsRepository
import dev.mtrp.app.ui.MtrpNavHost
import dev.mtrp.app.ui.theme.MtrpAppTheme
import dev.mtrp.core.MtrpSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * MTRP Android main activity.
 * Author: K. Bhanutej
 */
class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled gracefully — transports self-disable if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()

        setContent {
            MtrpAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    val contacts = remember { mutableStateListOf<Contact>() }
                    var sdkReady by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        // Collect incoming messages
                        if (sdkReady) {
                            MtrpSdk.api.incoming.collect { msg ->
                                val text     = msg.data.decodeToString()
                                val message  = ChatMessage(
                                    id           = UUID.randomUUID().toString(),
                                    senderNodeId = msg.senderNodeId,
                                    text         = text,
                                    channelName  = msg.channel.displayName,
                                    sentAtMs     = msg.receivedAtMs,
                                    isMine       = false
                                )
                                ContactsRepository.addMessage(msg.senderNodeId, message)
                            }
                        }
                    }

                    MtrpNavHost(
                        api      = if (sdkReady) MtrpSdk.api else null,
                        contacts = contacts,
                        onAddContact = { nodeId, name ->
                            ContactsRepository.addContact(nodeId, name)
                            contacts.clear()
                            contacts.addAll(ContactsRepository.contacts)
                        }
                    )
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val required = mutableListOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        val notGranted = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }
}
