package org.campus.autoconnect

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import android.widget.LinearLayout
import java.text.DateFormat
import java.util.Date

class ProtectionActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("profile", MODE_PRIVATE) }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val body = RejoinUi.screen(this, "Auto-reconnect")
        val health = BackgroundHealth.inspect(this)
        val enabled = RecoveryPrefs.enabled(this)
        val configured = prefs.getBoolean("http_opt_in", false) &&
            TrustedNetworks.all(this).any { CredentialVault.hasLoginFor(this, it) }

        val summary = RejoinUi.inset(this)
        val headline = when {
            !configured -> "Setup needed"
            !enabled -> "Auto-reconnect is off"
            else -> "Auto-reconnect is on"
        }
        val description = when {
            !configured -> "Save your campus Wi-Fi and login to use auto-reconnect."
            !enabled -> "Turn it on to sign in again when your campus Wi-Fi session expires."
            else -> "Rejoin can sign in when your saved campus Wi-Fi asks again."
        }
        RejoinUi.add(summary, RejoinUi.label(this, headline, 25f, bold = true).apply {
            RejoinUi.heading(this)
        })
        RejoinUi.add(summary, RejoinUi.label(this, description, 15f, RejoinUi.muted), 4)
        RejoinUi.add(body, summary)

        when {
            !configured -> RejoinUi.add(body, RejoinUi.action(this, "Finish setup") { finishSetup() }, 16)
            !enabled -> RejoinUi.add(body, RejoinUi.action(this, "Turn on auto-reconnect") {
                enable()
            }, 16)
            health.level == BackgroundHealth.Level.NEEDS_ACTION -> showAccessIssue(body, health)
        }
        RejoinUi.section(body, "Background checks")
        val previous = health.lastRun
        val lastChecked = if (previous > 0L)
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(previous))
        else "No background checks yet"
        RejoinUi.add(body, RejoinUi.label(this, lastChecked, 15f, bold = true), 8)
        RejoinUi.add(body, RejoinUi.label(this,
            if (previous > 0L) "Last background check. Android may delay checks while your phone sleeps."
            else "Android decides when background checks can run.", 14f, RejoinUi.muted), 2)
        val modeName = when (RecoveryPrefs.mode(this)) {
            RecoveryPolicy.Mode.BALANCED -> "Balanced"
            RecoveryPolicy.Mode.RESPONSIVE -> "Responsive"
        }
        RejoinUi.add(body, RejoinUi.navRow(this, "Check frequency", modeName) { choosePace() }, 12)
        if (enabled) {
            RejoinUi.add(body, RejoinUi.rule(this))
            RejoinUi.add(body, RejoinUi.smallAction(this, "Turn off auto-reconnect") { confirmTurnOff() }, 8)
        }
    }

    private fun showAccessIssue(body: LinearLayout, health: BackgroundHealth.State) {
        val access = RejoinUi.inset(this)
        RejoinUi.add(access, RejoinUi.label(this, health.title, 16f, RejoinUi.amber, true))
        RejoinUi.add(access, RejoinUi.label(this, health.description, 14f, RejoinUi.muted), 4)
        val restart = RejoinUi.hasWifiAccess(this) && health.backgroundLocation &&
            !health.restricted && !health.hasJob
        val locationOff = RejoinUi.hasWifiAccess(this) && !WifiProbe.locationEnabled(this)
        val label = when {
            locationOff -> "Open Location settings"
            restart -> "Restart background checks"
            else -> "Open app settings"
        }
        RejoinUi.add(access, RejoinUi.smallAction(this, label) {
            when {
                locationOff -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                restart -> enable()
                else -> RejoinUi.openAppSettings(this)
            }
        }, 4)
        RejoinUi.add(body, access, 16)
    }

    private fun finishSetup() {
        val profiles = TrustedNetworks.all(this)
        val accountSaved = CredentialVault.hasCredentials(this)
        val target = when {
            profiles.isEmpty() || !accountSaved || !prefs.getBoolean("http_opt_in", false) ->
                SetupActivity::class.java
            else -> NetworksActivity::class.java
        }
        startActivity(Intent(this, target))
    }

    private fun enable() {
        if (!RecoveryPrefs.enable(this)) {
            AlertDialog.Builder(this).setTitle("Couldn’t start background checks")
                .setMessage("Android didn’t accept the schedule. Try again.")
                .setPositiveButton("OK", null).show()
            return
        }
        render()
    }

    private fun confirmTurnOff() {
        AlertDialog.Builder(this)
            .setTitle("Turn off auto-reconnect?")
            .setMessage("You’ll need to reconnect manually when your campus session expires.")
            .setNegativeButton("Keep it on", null)
            .setPositiveButton("Turn off") { _, _ ->
                prefs.edit().putBoolean("auto_enabled", false).commit()
                RecoveryScheduler.disable(this)
                ActivityHistory.record(this, "Auto-reconnect paused")
                render()
            }.show()
    }

    private fun choosePace() {
        val options = arrayOf("Balanced · about every 30 minutes", "Responsive · about every 15 minutes")
        val choice = if (RecoveryPrefs.mode(this) == RecoveryPolicy.Mode.BALANCED) 0 else 1
        AlertDialog.Builder(this).setTitle("Check frequency")
            .setSingleChoiceItems(options, choice) { dialog, selected ->
                prefs.edit().putString("recovery_mode", if (selected == 0) "balanced" else "responsive").apply()
                if (RecoveryPrefs.enabled(this)) RecoveryScheduler.schedulePeriodic(this)
                dialog.dismiss()
                render()
            }
            .setNegativeButton("Cancel", null).show()
    }
}
