package dev.mtrp.app.data

import dev.mtrp.core.ChannelType

/**
 * App-layer data models.
 * These are local-only — never transmitted over the network.
 * Author: K. Bhanutej
 */

data class Contact(
    val nodeId:      String,
    val name:        String,
    val addedAtMs:   Long   = System.currentTimeMillis(),
    val lastSeenMs:  Long?  = null,
    val lastChannel: ChannelType? = null
)

data class ChatMessage(
    val id:          String,
    val senderNodeId: String,
    val text:        String,
    val channelName: String,
    val sentAtMs:    Long,
    val isMine:      Boolean,
    val isQueued:    Boolean = false
)

/**
 * In-memory contacts and message store.
 * Replace with Room database in a future phase for persistence.
 */
object ContactsRepository {
    private val _contacts  = mutableListOf<Contact>()
    private val _messages  = mutableMapOf<String, MutableList<ChatMessage>>()

    val contacts: List<Contact> get() = _contacts.toList()

    fun addContact(nodeId: String, name: String) {
        if (_contacts.none { it.nodeId == nodeId }) {
            _contacts.add(Contact(nodeId = nodeId, name = name))
        }
    }

    fun messagesFor(nodeId: String): List<ChatMessage> =
        _messages[nodeId]?.toList() ?: emptyList()

    fun addMessage(nodeId: String, message: ChatMessage) {
        _messages.getOrPut(nodeId) { mutableListOf() }.add(message)
    }

    fun contactByNodeId(nodeId: String): Contact? =
        _contacts.find { it.nodeId == nodeId }
}
