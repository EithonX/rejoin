package com.eithonx.rejoin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

class ProtectionActivity : RejoinActivity() {
    private val prefs by lazy { getSharedPreferences("profile", MODE_PRIVATE) }
    private var revision by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        revision++
    }

    override fun onWifiPermissionResult() { revision++ }

    @Composable
    override fun Screen() {
        val health = remember(revision) { BackgroundHealth.inspect(this) }
        val configured = remember(revision) {
            prefs.getBoolean("http_opt_in", false) &&
                TrustedNetworks.all(this).any { CredentialVault.hasLoginFor(this, it) }
        }
        val mode = remember(revision) { RecoveryPrefs.mode(this) }

        Page("Auto sign-in") {
            if (!configured && !health.enabled) {
                ScreenIntro("Finish setup", "Save a network and its login first.")
                PageButton("Continue setup") { finishSetup() }
            } else {
                ListGroup {
                    Row(Modifier.fillMaxWidth().toggleable(
                        value = health.enabled, role = Role.Switch,
                        onValueChange = { if (it) enable() else turnOff() }
                    ).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (health.enabled) "On" else "Off", style = MaterialTheme.typography.titleMedium)
                            BodyText("Reconnect when your Wi-Fi session expires.")
                        }
                        Switch(checked = health.enabled, onCheckedChange = null)
                    }
                }
                val issue = health.issue
                if (issue?.required == true) PermissionNotice(issue) { resolveBackgroundIssue(issue) }
                SectionLabel("Background checks")
                ListGroup { ActionRow("Fallback check frequency", mode.label) { choosePace(mode) } }
                if (issue == BackgroundHealth.Issue.OPTIMIZED) BodyText(issue.detail)
                if (health.lastRun > 0) BodyText("Last check " +
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(health.lastRun)))
            }
        }
    }

    private fun finishSetup() {
        val needsSetup = TrustedNetworks.all(this).isEmpty() || !CredentialVault.hasCredentials(this) ||
            !prefs.getBoolean("http_opt_in", false)
        if (needsSetup) open(SetupActivity::class.java) else open(NetworksActivity::class.java)
    }

    private fun enable() {
        if (RecoveryPrefs.enable(this)) revision++
        else notice("Couldn’t start background checks", "Try again in a moment.")
    }

    private fun turnOff() {
        prefs.edit().putBoolean("auto_enabled", false).commit()
        RecoveryScheduler.disable(this)
        ActivityHistory.record(this, "Auto sign-in turned off")
        revision++
    }

    private fun choosePace(current: RecoveryPolicy.Mode) {
        showDialog { dismiss ->
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("Fallback check frequency") },
                text = {
                    ListGroup {
                        RecoveryPolicy.Mode.entries.forEach { mode ->
                            val interval = if (mode == RecoveryPolicy.Mode.BALANCED) "30" else "15"
                            val detail = "About every $interval min" + if (mode == current) " · Selected" else ""
                            ActionRow(mode.label, detail) {
                                val value = if (mode == RecoveryPolicy.Mode.BALANCED) "balanced" else "responsive"
                                prefs.edit().putString("recovery_mode", value).apply()
                                if (RecoveryPrefs.enabled(this)) RecoveryScheduler.schedulePeriodic(this)
                                dismiss()
                                revision++
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
            )
        }
    }

    private val RecoveryPolicy.Mode.label: String
        get() = if (this == RecoveryPolicy.Mode.BALANCED) "Balanced" else "Responsive"
}
