package com.eithonx.rejoin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal enum class ConnectionTone { NEUTRAL, ONLINE, ATTENTION, ERROR, WORKING }

internal data class HomeStatus(
    val title: String,
    val detail: String? = null,
    val tone: ConnectionTone = ConnectionTone.NEUTRAL,
    val actionLabel: String? = null,
    val action: () -> Unit = {}
)

internal data class HomeUi(
    val status: HomeStatus = HomeStatus("Checking Wi-Fi", tone = ConnectionTone.WORKING),
    val network: String? = null,
    val automation: String = "Off",
    val lastChecked: String? = null
)

@Composable
internal fun HomeScreen(
    ui: HomeUi,
    onSettings: () -> Unit,
    onAutomation: () -> Unit,
    onActivity: () -> Unit
) {
    RejoinPage("Rejoin", actions = {
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
    }) {
        ConnectionCard(ui.status, ui.network)
        SectionLabel("Connection")
        ListGroup {
            ActionRow("Auto sign-in", ui.automation, R.drawable.ic_shield, onAutomation)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ActionRow("Activity", ui.lastChecked ?: "Sign-ins and changes", R.drawable.ic_activity, onActivity)
        }
    }
}

@Composable
private fun ConnectionCard(status: HomeStatus, network: String?) {
    val colors = MaterialTheme.colorScheme
    val icon = when (status.tone) {
        ConnectionTone.ONLINE -> Icons.Filled.CheckCircle
        ConnectionTone.ATTENTION -> Icons.Filled.Warning
        ConnectionTone.ERROR -> Icons.Filled.Close
        ConnectionTone.WORKING -> Icons.Filled.Refresh
        ConnectionTone.NEUTRAL -> Icons.Filled.Info
    }
    val iconTint = when (status.tone) {
        ConnectionTone.ONLINE -> colors.tertiary
        ConnectionTone.ATTENTION -> colors.secondary
        ConnectionTone.ERROR -> colors.error
        else -> colors.primary
    }
    Surface(color = colors.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            ) {
                Icon(icon, null, modifier = Modifier.size(26.dp), tint = iconTint)
                Text(status.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            }
            if (network != null) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_wifi), null,
                        modifier = Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                    Text(network, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
            }
            status.detail?.let { BodyText(it) }
            if (status.tone == ConnectionTone.WORKING) LinearProgressIndicator(Modifier.fillMaxWidth())
            status.actionLabel?.let {
                PageButton(it, filled = status.tone != ConnectionTone.ONLINE, action = status.action)
            }
        }
    }
}
