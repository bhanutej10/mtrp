package dev.mtrp.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.mtrp.core.api.MtrpApi
import kotlinx.coroutines.launch

/**
 * Profile screen — shows node ID, copy and QR share options.
 * Author: K. Bhanutej
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(api: MtrpApi) {
    val snackbarState  = remember { SnackbarHostState() }
    val scope          = rememberCoroutineScope()
    val clipboard      = LocalClipboardManager.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("My Profile") }) },
        snackbarHost = { SnackbarHost(snackbarState) }
    ) { padding ->
        Column(
            modifier              = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Avatar
            Box(
                modifier         = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = "M",
                    style      = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Text(
                text  = "Your Node ID",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Node ID card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text       = api.nodeId,
                        fontFamily = FontFamily.Monospace,
                        style      = MaterialTheme.typography.bodyMedium,
                        color      = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(api.nodeId))
                                scope.launch { snackbarState.showSnackbar("Node ID copied") }
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                        IconButton(
                            onClick = { /* QR generation — Phase 14.1 */ }
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = "Show QR")
                        }
                    }
                }
            }

            Text(
                text  = "Share this ID with others so they can message you. " +
                        "It never changes unless you reset your identity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick  = { /* QR share sheet — Phase 14.1 */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Share via QR Code")
            }
        }
    }
}
