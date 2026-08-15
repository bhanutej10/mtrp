package dev.mtrp.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mtrp.core.api.MtrpApi
import dev.mtrp.desktop.ui.MessagesPanel
import dev.mtrp.desktop.ui.MtrpTheme
import dev.mtrp.desktop.ui.PeersPanel
import dev.mtrp.desktop.ui.RelayStatusPanel
import dev.mtrp.desktop.ui.SettingsPanel

/**
 * Root composable for the MTRP desktop application.
 * Navigation rail on the left, content panel on the right.
 * Author: K. Bhanutej
 */
@Composable
fun DesktopApp(api: MtrpApi) {
    MtrpTheme {
        var selectedTab by remember { mutableStateOf(Tab.RELAY) }

        Row(modifier = Modifier.fillMaxSize()) {

            NavigationRail(modifier = Modifier.fillMaxHeight().width(72.dp)) {
                NavigationRailItem(
                    selected  = selectedTab == Tab.RELAY,
                    onClick   = { selectedTab = Tab.RELAY },
                    icon      = { Icon(Icons.Default.Hub,     contentDescription = "Relay") },
                    label     = { Text("Relay") }
                )
                NavigationRailItem(
                    selected  = selectedTab == Tab.MESSAGES,
                    onClick   = { selectedTab = Tab.MESSAGES },
                    icon      = { Icon(Icons.Default.Message, contentDescription = "Messages") },
                    label     = { Text("Messages") }
                )
                NavigationRailItem(
                    selected  = selectedTab == Tab.PEERS,
                    onClick   = { selectedTab = Tab.PEERS },
                    icon      = { Icon(Icons.Default.People,  contentDescription = "Peers") },
                    label     = { Text("Peers") }
                )
                NavigationRailItem(
                    selected  = selectedTab == Tab.SETTINGS,
                    onClick   = { selectedTab = Tab.SETTINGS },
                    icon      = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label     = { Text("Settings") }
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    Tab.RELAY    -> RelayStatusPanel(api)
                    Tab.MESSAGES -> MessagesPanel(api)
                    Tab.PEERS    -> PeersPanel(api)
                    Tab.SETTINGS -> SettingsPanel(api)
                }
            }
        }
    }
}

enum class Tab { RELAY, MESSAGES, PEERS, SETTINGS }
