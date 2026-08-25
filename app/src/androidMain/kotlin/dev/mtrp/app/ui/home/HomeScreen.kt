package dev.mtrp.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.mtrp.app.data.Contact
import dev.mtrp.core.ChannelType
import dev.mtrp.core.api.MtrpApi

/**
 * Home screen — channel status strip + recent contacts.
 * Author: K. Bhanutej
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    api:            MtrpApi?,
    contacts:       List<Contact>,
    onOpenChat:     (Contact) -> Unit,
    onOpenContacts: () -> Unit,
    onOpenProfile:  () -> Unit
) {
    val activeChannels by (api?.activeChannels?.collectAsState()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyList()) })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MTRP") },
                actions = {
                    IconButton(onClick = onOpenContacts) {
                        Icon(Icons.Default.Contacts, contentDescription = "Contacts")
                    }
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier        = Modifier.fillMaxSize().padding(padding),
            contentPadding  = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text  = "Active Channels",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ChannelType.entries.filter { it != ChannelType.QUEUED }) { channel ->
                        ChannelChip(
                            channel  = channel,
                            isActive = activeChannels.contains(channel)
                        )
                    }
                }
            }

            item {
                Text(
                    text  = "Contacts",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (contacts.isEmpty()) {
                item {
                    Text(
                        text  = "No contacts yet. Tap the contacts icon to add one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(contacts, key = { it.nodeId }) { contact ->
                    ContactRow(contact = contact, onClick = { onOpenChat(contact) })
                }
            }
        }
    }
}

@Composable
private fun ChannelChip(channel: ChannelType, isActive: Boolean) {
    val bgColor   = if (isActive) MaterialTheme.colorScheme.primaryContainer
                    else          MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                    else          MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = Icons.Default.Hub,
                contentDescription = null,
                modifier           = Modifier.size(14.dp),
                tint               = if (isActive) Color(0xFF3DD68C) else textColor
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text  = channel.displayName.substringBefore(" "),
                style = MaterialTheme.typography.labelSmall,
                color = textColor
            )
        }
    }
}

@Composable
private fun ContactRow(contact: Contact, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = contact.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text  = contact.nodeId.take(12) + "...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
