package com.eithonx.rejoin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

@Composable
internal fun PermissionNotice(issue: BackgroundHealth.Issue, onAction: () -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .3f
    val container = if (dark) Color(0xFF392B20) else Color(0xFFFFEFD9)
    val ink = if (dark) Color(0xFFFFD5AC) else Color(0xFF56371D)
    Surface(
        color = container,
        contentColor = ink,
        border = BorderStroke(1.dp, ink.copy(alpha = 0.22f)),
        shape = MaterialTheme.shapes.large
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Warning, null, Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(issue.title, style = MaterialTheme.typography.titleSmall)
                Text(issue.detail, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onAction) { Text(issue.action, color = ink) }
            }
        }
    }
}
