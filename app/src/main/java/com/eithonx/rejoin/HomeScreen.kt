package com.eithonx.rejoin

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
    val lastChecked: String? = null,
    val issue: BackgroundHealth.Issue? = null
)

internal val relayBackground = Color(0xFF263444)
internal val relayForeground = Color(0xFFF8F0E3)
private val relayMuted = Color(0xFFCAD5DF)

@Composable
internal fun HomeScreen(
    ui: HomeUi,
    updateVersion: String?,
    onSettings: () -> Unit,
    onAutomation: () -> Unit,
    onActivity: () -> Unit,
    onIssue: (BackgroundHealth.Issue) -> Unit,
    onUpdate: () -> Unit
) {
    RejoinPage("Rejoin", wordmark = true, actions = {
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, "Settings", modifier = Modifier.size(21.dp))
        }
    }) {
        ConnectionCard(ui.status, ui.network)
        ui.issue?.let { PermissionNotice(it) { onIssue(it) } }
        if (updateVersion != null) ListGroup {
            ActionRow("Update available", "Version $updateVersion", R.drawable.ic_about, onUpdate)
        }
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
    Surface(
        color = relayBackground,
        contentColor = relayForeground,
        border = BorderStroke(1.dp, Color(0xFF42566C)),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (status.tone == ConnectionTone.ONLINE) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Filled.CheckCircle, null, Modifier.size(15.dp), tint = Color(0xFFD8EBFA))
                            Text("INTERNET AVAILABLE", style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFD8EBFA))
                        }
                    } else Text("WI-FI STATUS", style = MaterialTheme.typography.labelSmall, color = relayMuted)
                    Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        AnimatedContent(
                            targetState = status.title,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(100)) },
                            label = "Connection status"
                        ) { title ->
                            Text(title, Modifier.semantics { heading() },
                                style = MaterialTheme.typography.headlineLarge)
                        }
                    }
                    if (network != null) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(painterResource(R.drawable.ic_wifi), null,
                                modifier = Modifier.size(16.dp), tint = relayMuted)
                            Text(network, style = MaterialTheme.typography.bodyMedium, color = relayMuted)
                        }
                    }
                }
                if (LocalDensity.current.fontScale < 1.25f) {
                    RelayArtwork(
                        if (status.tone == ConnectionTone.ONLINE) RelayScene.CONNECTED else RelayScene.WAITING,
                        Modifier.fillMaxWidth(0.44f).height(114.dp)
                    )
                }
            }
            status.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = relayMuted) }
            if (status.tone == ConnectionTone.WORKING) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = relayMuted, trackColor = Color(0xFF42566C))
            }
            if (status.tone == ConnectionTone.ONLINE) {
                status.actionLabel?.let { label ->
                    TextButton(onClick = status.action, modifier = Modifier.align(Alignment.Start)) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(17.dp), tint = relayForeground)
                        Text(label, Modifier.padding(start = 6.dp), color = relayForeground)
                    }
                }
            } else if (status.actionLabel != null) {
                val glyph = when (status.tone) {
                    ConnectionTone.ATTENTION -> Icons.Filled.Warning
                    ConnectionTone.ERROR -> Icons.Filled.Close
                    else -> Icons.Filled.Info
                }
                val tint = when (status.tone) {
                    ConnectionTone.ATTENTION -> Color(0xFFF3BE8D)
                    ConnectionTone.ERROR -> Color(0xFFFFB4A9)
                    else -> relayMuted
                }
                Button(
                    onClick = status.action,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = tint, contentColor = relayBackground)
                ) {
                    Icon(glyph, null, Modifier.size(16.dp))
                    Text(status.actionLabel, Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
