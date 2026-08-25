package dev.mtrp.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import dev.mtrp.app.data.Contact
import dev.mtrp.app.data.ContactsRepository
import dev.mtrp.app.ui.chat.ChatScreen
import dev.mtrp.app.ui.contacts.ContactsScreen
import dev.mtrp.app.ui.home.HomeScreen
import dev.mtrp.app.ui.profile.ProfileScreen
import dev.mtrp.core.api.MtrpApi

/**
 * Simple stack-based navigation for the MTRP Android app.
 * Replace with Compose Navigation library in a future phase.
 * Author: K. Bhanutej
 */
sealed class Screen {
    object Home     : Screen()
    object Contacts : Screen()
    object Profile  : Screen()
    data class Chat(val contact: Contact) : Screen()
}

@Composable
fun MtrpNavHost(
    api:          MtrpApi?,
    contacts:     SnapshotStateList<Contact>,
    onAddContact: (nodeId: String, name: String) -> Unit
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(
            api          = api,
            contacts     = contacts,
            onOpenChat   = { screen = Screen.Chat(it) },
            onOpenContacts = { screen = Screen.Contacts },
            onOpenProfile  = { screen = Screen.Profile }
        )
        is Screen.Contacts -> ContactsScreen(
            contacts = contacts,
            onAdd    = { nodeId, name ->
                onAddContact(nodeId, name)
                screen = Screen.Home
            },
            onSelect = { screen = Screen.Chat(it) }
        )
        is Screen.Profile -> ProfileScreen(api = api ?: return)
        is Screen.Chat -> ChatScreen(
            contact  = s.contact,
            messages = ContactsRepository.messagesFor(s.contact.nodeId),
            api      = api ?: return,
            onBack   = { screen = Screen.Home }
        )
    }
}
