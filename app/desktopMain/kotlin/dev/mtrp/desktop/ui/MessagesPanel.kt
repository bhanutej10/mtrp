package dev.mtrp.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mtrp.core.api.IncomingMessage
import dev.mtrp.core.api.MtrpApi
import dev.mtrp.core.api.SendResult
import kotlinx.coroutines.launch

/**
 * Messages panel — send and receive messages.
 * Author: K. Bhanutej
 */
@Composable
fun MessagesPanel(api: MtrpApi) {
    val scope        = rememberCoroutineScope()
    val messages     = remember { mutableStateListOf<DisplayMessage>() }
    val listState    = rememberLazyListState()
    var destNodeId   by remember { mutableStateOf("") }
    var messageText  by remember { mutableStateOf("") }
    var statusText   by remember { mutableStateOf("") }

    // Collect incoming messages
    LaunchedEffect(Unit) {
        api.incoming.collect { msg ->
            messages.add(
                DisplayMessage(
                    sender  = msg.senderNodeId.take(8),
                    text    = msg.data.decodeToString(),
                    channel = msg.channel.displayName,
                    isMine  = false
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text  = "Messages",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value         = destNodeId,
            onValueChange = { destNodeId = it },
            label         = { Text("Destination Node ID") },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            state    = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(messages) { msg ->
                MessageBubble(msg)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        if (statusText.isNotEmpty()) {
            Text(
                text  = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier             = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment    = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = messageText,
                onValueChange = { messageText = it },
                label         = { Text("Message") },
                modifier      = Modifier.weight(1f),
                singleLine    = true
            )
            Button(
                onClick = {
                    if (destNodeId.isBlank() || messageText.isBlank()) return@Button
                    val text = messageText
                    val dest = destNodeId
                    messageText = ""
                    scope.launch {
                        val result = api.send(dest, text.encodeToByteArray())
                        statusText = when (result) {
                            is SendResult.Sent   -> "Sent via ${result.channel.displayName}"
                            is SendResult.Queued -> "Queued for later delivery"
                            is SendResult.Failed -> "Failed: ${result.reason}"
                        }
                        messages.add(
                            DisplayMessage(
                                sender  = "You",
                                text    = text,
                                channel = if (result is SendResult.Sent) result.channel.displayName else "Queue",
                                isMine  = true
                            )
                        )
                    }
                }
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: DisplayMessage) {
    val bgColor = if (msg.isMine)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    else
        MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(8.dp),
        colors   = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text  = msg.sender,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text  = msg.channel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text  = msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

data class DisplayMessage(
    val sender:  String,
    val text:    String,
    val channel: String,
    val isMine:  Boolean
)
