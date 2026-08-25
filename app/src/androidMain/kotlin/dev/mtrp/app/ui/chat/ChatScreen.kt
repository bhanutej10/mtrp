package dev.mtrp.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mtrp.app.data.ChatMessage
import dev.mtrp.app.data.Contact
import dev.mtrp.core.api.MtrpApi
import dev.mtrp.core.api.SendResult
import kotlinx.coroutines.launch

/**
 * Chat screen — per-contact message thread.
 * Author: K. Bhanutej
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    contact:  Contact,
    messages: List<ChatMessage>,
    api:      MtrpApi,
    onBack:   () -> Unit
) {
    val scope     = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var text      by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(contact.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            text  = contact.nodeId.take(12) + "...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            LazyColumn(
                state    = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(msg)
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            if (statusMsg.isNotEmpty()) {
                Text(
                    text     = statusMsg,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    modifier      = Modifier.weight(1f),
                    placeholder   = { Text("Message") },
                    shape         = RoundedCornerShape(24.dp),
                    maxLines      = 4
                )
                IconButton(
                    onClick = {
                        if (text.isBlank()) return@IconButton
                        val msg = text
                        text = ""
                        scope.launch {
                            val result = api.send(contact.nodeId, msg.encodeToByteArray())
                            statusMsg = when (result) {
                                is SendResult.Sent   -> "Sent via ${result.channel.displayName}"
                                is SendResult.Queued -> "Queued — will send when reachable"
                                is SendResult.Failed -> "Failed: ${result.reason}"
                            }
                        }
                    }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val alignment = if (msg.isMine) Alignment.End else Alignment.Start
    val bgColor   = if (msg.isMine)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (msg.isMine)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier              = Modifier.fillMaxWidth(),
        horizontalAlignment   = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = bgColor,
                    shape = RoundedCornerShape(
                        topStart    = 16.dp,
                        topEnd      = 16.dp,
                        bottomStart = if (msg.isMine) 16.dp else 4.dp,
                        bottomEnd   = if (msg.isMine) 4.dp else 16.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text  = msg.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text  = msg.channelName,
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }
    }
}
