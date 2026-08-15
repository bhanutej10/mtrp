package dev.mtrp.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.mtrp.core.MTRP
import dev.mtrp.core.api.MtrpApi

/**
 * Settings panel — node info and protocol constants.
 * Author: K. Bhanutej
 */
@Composable
fun SettingsPanel(api: MtrpApi) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text  = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(20.dp))

        SectionCard(title = "Node Identity") {
            SettingRow("Node ID", api.nodeId)
            SettingRow("Protocol", MTRP.version())
            SettingRow("Spec version", MTRP.SPEC_VERSION)
            SettingRow("Author", MTRP.AUTHOR)
        }

        Spacer(modifier = Modifier.height(12.dp))

        SectionCard(title = "Protocol Constants") {
            SettingRow("Max hops (TTL)", MTRP.MAX_HOPS.toString())
            SettingRow("Message TTL", "${MTRP.TTL_HOURS} hours")
            SettingRow("Queue max total", MTRP.QUEUE_MAX_TOTAL.toString())
            SettingRow("Queue max per origin", MTRP.QUEUE_MAX_PER_ORIGIN.toString())
            SettingRow("Dedup cache size", MTRP.DEDUP_CACHE_SIZE.toString())
            SettingRow("Max skipped keys", MTRP.MAX_SKIPPED_KEYS.toString())
        }

        Spacer(modifier = Modifier.height(12.dp))

        SectionCard(title = "Queue") {
            SettingRow("Pending messages", api.queueSize.toString())
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(10.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text  = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color      = MaterialTheme.colorScheme.onBackground
        )
    }
    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
}
