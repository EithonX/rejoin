package com.eithonx.rejoin

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

    @Composable
    override fun Screen() {
        val health = remember(revision) { BackgroundHealth.inspect(this) }
        val enabled = remember(revision) { RecoveryPrefs.enabled(this) }
        val configured = remember(revision) {
            prefs.getBoolean("http_opt_in", false) &&
                TrustedNetworks.all(this).any { CredentialVault.hasLoginFor(this, it) }
        }
        val mode = remember(revision) { RecoveryPrefs.mode(this) }

        Page("Auto sign-in") {
            when {
                !configured -> {
                    ScreenIntro("Setup needed", "Save a network and its login to use auto sign-in.")
                    PageButton("Finish setup") { finishSetup() }
                }
                !enabled -> {
                    ScreenIntro("Auto sign-in is off", "Turn it on to sign in again when your session expires.")
                    PageButton("Turn on auto sign-in") { enable() }
                }
                else -> {
                    ScreenIntro("Auto sign-in is on", "Rejoin checks your saved Wi-Fi in the background.")
                    if (health.level == BackgroundHealth.Level.NEEDS_ACTION) AccessIssue(health)
                }
            }

            SectionLabel("Background checks")
            ListGroup {
                ActionRow("Check frequency", mode.label) { choosePace(mode) }
            }
            val lastCheck = if (health.lastRun > 0)
                "Last check " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(health.lastRun)) else "No background checks yet."
            BodyText(lastCheck)
            if (enabled) TextButton(onClick = ::confirmTurnOff) { Text("Turn off auto sign-in") }
        }
    }

    @Composable
    private fun AccessIssue(health: BackgroundHealth.State) {
        val restart = hasWifiAccess() && health.backgroundLocation && !health.restricted && !health.hasJob
        val locationOff = hasWifiAccess() && !WifiProbe.locationEnabled(this)
        val label = when {
            locationOff -> "Open Location settings"
            restart -> "Restart checks"
            else -> "Open app settings"
        }
        ListGroup {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(health.title, style = MaterialTheme.typography.titleMedium)
                BodyText(health.description)
                TextButton(onClick = {
                    when {
                        locationOff -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        restart -> enable()
                        else -> openAppSettings()
                    }
                }) { Text(label) }
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

    private fun confirmTurnOff() {
        confirm("Turn off auto sign-in?", "You can still sign in from Home.",
            "Turn off", "Keep on") {
            prefs.edit().putBoolean("auto_enabled", false).commit()
            RecoveryScheduler.disable(this)
            ActivityHistory.record(this, "Auto sign-in turned off")
            revision++
        }
    }

    private fun choosePace(current: RecoveryPolicy.Mode) {
        showDialog { dismiss ->
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("Check frequency") },
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
