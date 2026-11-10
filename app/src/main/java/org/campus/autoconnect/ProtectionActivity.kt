package org.campus.autoconnect

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

/**
 * Deliberate opt-in instead of a switch with ambiguous state:
 * one action to enable, a clear active state, a separate deliberate pause.
 */
class ProtectionActivity : Activity() {
    private lateinit var headline: TextView
    private lateinit var description: TextView
    private lateinit var statusLabel: TextView
    private lateinit var action: TextView
    private lateinit var secondary: TextView
    private lateinit var lastRun: TextView
    private lateinit var help: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = ScrollView(this).apply {
            setBackgroundColor(RejoinUi.canvas)
            isFillViewport = true
        }
        val body = RejoinUi.column(this).apply {
            val side = RejoinUi.dp(this@ProtectionActivity, 24)
            setPadding(side, side, side, RejoinUi.dp(this@ProtectionActivity, 40))
        }
        page.addView(body)
        setContentView(page)
        RejoinUi.applySystemBars(this, page)
        RejoinUi.header(this, body, "Auto-reconnect")

        val glyph = RejoinUi.icon(this, R.drawable.ic_shield, 37)
        RejoinUi.add(body, glyph, 52)
        statusLabel = RejoinUi.label(this, "OFF", 11f, RejoinUi.muted, true).apply {
            letterSpacing = .16f
        }
        RejoinUi.add(body, statusLabel, 25)
        headline = RejoinUi.label(this, "", 29f, RejoinUi.ink, true).apply {
            maxLines = 3
        }
        RejoinUi.add(body, headline, 11)
        description = RejoinUi.label(this, "", 14f, RejoinUi.muted).apply {
            setLineSpacing(RejoinUi.dp(this@ProtectionActivity, 4).toFloat(), 1f)
        }
        RejoinUi.add(body, description, 12)

        action = RejoinUi.action(this, "Enable auto-reconnect") { enable() }
        RejoinUi.add(body, action, 33)
        secondary = RejoinUi.smallAction(this, "Pause auto-reconnect") { confirmPause() }
        secondary.gravity = Gravity.CENTER
        RejoinUi.add(body, secondary, 8)

        RejoinUi.section(body, "Background checks", 38)
        val info = RejoinUi.inset(this)
        RejoinUi.add(body, info, 12)
        lastRun = RejoinUi.label(this, "", 13f, RejoinUi.ink, true)
        RejoinUi.add(info, lastRun)
        RejoinUi.add(info, RejoinUi.label(this,
            "Android manages the timing. Rejoin doesn't run a continuous connection loop.",
            12f, RejoinUi.muted), 10)
        help = RejoinUi.smallAction(this, "Review background access") { backgroundHelp() }
        RejoinUi.add(info, help, 11)
        render()
    }

    private fun configured(): Boolean {
        val profiles = TrustedNetworks.all(this)
        val consent = getSharedPreferences("profile", MODE_PRIVATE)
            .getBoolean("http_opt_in", false)
        return consent && profiles.any { CredentialVault.hasLoginFor(this, it) }
    }

    private fun render() {
        if (!::headline.isInitialized) return
        val enabled = RecoveryPrefs.enabled(this)
        val health = BackgroundHealth.inspect(this)
        val ready = configured()

        statusLabel.text = when {
            !ready -> "SETUP REQUIRED"
            !enabled -> "OFF"
            health.level == BackgroundHealth.Level.NEEDS_ACTION -> "NEEDS ATTENTION"
            else -> "ON"
        }
        statusLabel.setTextColor(when {
            !ready || health.level == BackgroundHealth.Level.NEEDS_ACTION &&
                enabled -> RejoinUi.amber
            enabled -> RejoinUi.jade
            else -> RejoinUi.muted
        })
        headline.text = when {
            !ready -> "Finish setting up Rejoin."
            !enabled -> "Stay connected without checking."
            health.level == BackgroundHealth.Level.NEEDS_ACTION ->
                "One setting needs attention."
            else -> "Rejoin is looking after your Wi-Fi."
        }
        description.text = when {
            !ready -> "Save your Wi-Fi and campus login once to get started."
            !enabled -> "When your campus asks you to sign in again, Rejoin can restore access."
            health.level == BackgroundHealth.Level.NEEDS_ACTION -> health.description
            else -> "Automatic checks are on. Rejoin stays quiet while you're connected."
        }

        action.visibility = if (!enabled || !ready) View.VISIBLE else View.GONE
        action.text = if (ready) "Enable auto-reconnect" else "Finish setup"
        action.setOnClickListener {
            if (ready) enable() else startActivity(Intent(this, SetupActivity::class.java))
        }
        secondary.visibility = if (enabled) View.VISIBLE else View.GONE
        help.text = if (health.level == BackgroundHealth.Level.NEEDS_ACTION)
            "Fix background access  →" else "Background access  →"
        val previous = health.lastRun
        lastRun.text = if (previous > 0L)
            "Last background check · " +
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(previous))
        else "No background check recorded yet"
    }

    private fun enable() {
        if (!configured()) {
            startActivity(Intent(this, SetupActivity::class.java))
            return
        }
        val prefs = getSharedPreferences("profile", MODE_PRIVATE)
        prefs.edit().putBoolean("auto_enabled", true).commit()
        val successful = RecoveryScheduler.schedule(this, 15_000L)
        if (!successful) {
            prefs.edit().putBoolean("auto_enabled", false).commit()
            AlertDialog.Builder(this).setTitle("Could not enable auto-reconnect")
                .setMessage("Android didn't accept the background schedule. Please try again.")
                .setPositiveButton("OK", null).show()
            render()
            return
        }
        RecoveryScheduler.registerWifiEvents(this)
        ActivityHistory.record(this, "Auto-reconnect enabled")
        render()
    }

    private fun confirmPause() {
        AlertDialog.Builder(this)
            .setTitle("Pause auto-reconnect?")
            .setMessage("Rejoin won't sign you in automatically until you turn it on again.")
            .setNegativeButton("Keep it on", null)
            .setPositiveButton("Pause") { _, _ ->
                getSharedPreferences("profile", MODE_PRIVATE).edit()
                    .putBoolean("auto_enabled", false).commit()
                RecoveryScheduler.disable(this)
                ActivityHistory.record(this, "Auto-reconnect paused")
                render()
            }.show()
    }

    private fun backgroundHelp() {
        val health = BackgroundHealth.inspect(this)
        val note = when {
            !health.backgroundLocation ->
                "Allow Location access all the time so Rejoin can recognize your saved Wi-Fi when the app is closed."
            health.restricted || !health.batteryUnrestricted ->
                "Android may restrict background checks. Open Rejoin's app settings and allow background activity if you want more reliable unattended recovery."
            else ->
                "Android will manage Rejoin's checks. The phone can delay them during deep sleep."
        }
        AlertDialog.Builder(this).setTitle("Background access")
            .setMessage(note)
            .setNegativeButton("Close", null)
            .setPositiveButton("Open app settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName)))
            }.show()
    }

    override fun onResume() {
        super.onResume()
        if (::headline.isInitialized) render()
    }
}
