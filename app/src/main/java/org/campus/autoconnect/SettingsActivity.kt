package org.campus.autoconnect

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/**
 * Settings, not a sales page: short rows, clear outcomes, advanced tests hidden.
 */
class SettingsActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var networksRow: LinearLayout
    private lateinit var accountsRow: LinearLayout
    private lateinit var paceRow: LinearLayout
    private lateinit var backgroundRow: LinearLayout
    private lateinit var advancedBody: LinearLayout
    private lateinit var advancedToggle: TextView
    private lateinit var setupCaption: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this).apply {
            setBackgroundColor(RejoinUi.canvas)
            clipToPadding = false
        }
        val body = RejoinUi.column(this).apply {
            setPadding(RejoinUi.dp(this@SettingsActivity, 24),
                RejoinUi.dp(this@SettingsActivity, 24),
                RejoinUi.dp(this@SettingsActivity, 24),
                RejoinUi.dp(this@SettingsActivity, 32))
        }
        root.addView(body)
        setContentView(root)
        RejoinUi.applySystemBars(this, root)
        RejoinUi.header(this, body, "Settings")

        setupCaption = RejoinUi.label(this, "", 13f, RejoinUi.muted)
        RejoinUi.add(body, setupCaption, 17)

        RejoinUi.section(body, "Your setup", 29)
        val setupGroup = RejoinUi.inset(this)
        RejoinUi.add(body, setupGroup, 12)
        networksRow = RejoinUi.column(this)
        accountsRow = RejoinUi.column(this)
        RejoinUi.add(setupGroup, networksRow)
        RejoinUi.add(setupGroup, RejoinUi.rule(this), 4)
        RejoinUi.add(setupGroup, accountsRow)

        RejoinUi.section(body, "Automatic recovery", 29)
        val recoveryGroup = RejoinUi.inset(this)
        RejoinUi.add(body, recoveryGroup, 12)
        paceRow = RejoinUi.column(this)
        backgroundRow = RejoinUi.column(this)
        RejoinUi.add(recoveryGroup, paceRow)
        RejoinUi.add(recoveryGroup, RejoinUi.rule(this), 4)
        RejoinUi.add(recoveryGroup, backgroundRow)

        RejoinUi.section(body, "More", 29)
        val moreGroup = RejoinUi.inset(this)
        RejoinUi.add(body, moreGroup, 12)
        RejoinUi.add(moreGroup, RejoinUi.navRow(this,
            "Activity", "Connection events on this phone") {
            startActivity(Intent(this, ActivityLogActivity::class.java))
        })
        RejoinUi.add(moreGroup, RejoinUi.rule(this), 4)
        RejoinUi.add(moreGroup, RejoinUi.navRow(this, "Developer", "EithonX on GitHub") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/EithonX")))
        })

        RejoinUi.section(body, "Support", 29)
        val tools = RejoinUi.inset(this)
        RejoinUi.add(body, tools, 12)
        advancedToggle = RejoinUi.smallAction(this, "Troubleshooting  +") {
            advancedBody.visibility =
                if (advancedBody.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            advancedToggle.text = if (advancedBody.visibility == View.VISIBLE)
                "Troubleshooting  −" else "Troubleshooting  +"
        }
        RejoinUi.add(tools, advancedToggle)
        advancedBody = RejoinUi.column(this).apply { visibility = View.GONE }
        RejoinUi.add(tools, advancedBody, 8)
        RejoinUi.add(advancedBody, RejoinUi.label(this,
            "Only use these controls when diagnosing a connection problem.",
            12f, RejoinUi.muted), 2)
        RejoinUi.add(advancedBody, RejoinUi.action(this, "Check internet", false) {
            runTest("check")
        }, 14)
        RejoinUi.add(advancedBody, RejoinUi.action(this, "Try one sign-in", false) {
            confirmLogin()
        }, 9)
        RejoinUi.add(advancedBody, RejoinUi.action(this, "Open campus sign-in page", false) {
            if (!LiveSessionTest.selectedWifiMatches(this)) {
                message("Connect to saved Wi-Fi first", "The campus sign-in page is only for your saved network.")
            } else startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("http://172.16.2.1:1000/login?")))
        }, 9)
        RejoinUi.add(advancedBody, RejoinUi.action(this, "Log out shared session", false) {
            confirmLogout()
        }, 9)
        RejoinUi.add(advancedBody, RejoinUi.action(this, "Erase all saved logins", false) {
            confirmErase()
        }, 9)

        RejoinUi.add(body, RejoinUi.label(this,
            "Rejoin 2.3 beta   ·   Made by EithonX", 12f, RejoinUi.muted), 32)
        refresh()
    }

    private fun refresh() {
        if (!::networksRow.isInitialized) return
        networksRow.removeAllViews()
        accountsRow.removeAllViews()
        paceRow.removeAllViews()
        backgroundRow.removeAllViews()
        val profiles = TrustedNetworks.all(this)
        val logins = CredentialVault.list(this)
        setupCaption.text = if (profiles.isEmpty() || logins.isEmpty())
            "Finish setup to let Rejoin connect automatically."
        else "Your networks and logins are saved on this phone."

        networksRow.addView(RejoinUi.navRow(this, "Trusted Wi-Fi",
            when (profiles.size) {
                0 -> "No networks saved"
                1 -> "1 network saved"
                else -> profiles.size.toString() + " networks saved"
            }) {
            startActivity(Intent(this, NetworksActivity::class.java))
        })
        accountsRow.addView(RejoinUi.navRow(this, "Saved logins",
            when (logins.size) {
                0 -> "No login saved"
                1 -> "1 password saved"
                else -> logins.size.toString() + " passwords saved"
            }) {
            startActivity(Intent(this, LoginsActivity::class.java))
        })
        val current = if (RecoveryPrefs.mode(this) == RecoveryPolicy.Mode.BALANCED)
            "Balanced" else "Responsive"
        paceRow.addView(RejoinUi.navRow(this, "Recovery pace", current) {
            choosePace()
        })
        val health = BackgroundHealth.inspect(this)
        backgroundRow.addView(RejoinUi.navRow(this, "Background access",
            when (health.level) {
                BackgroundHealth.Level.READY -> "Ready"
                BackgroundHealth.Level.PAUSED -> "Auto-reconnect is off"
                BackgroundHealth.Level.NEEDS_ACTION -> "Needs attention"
            }) {
            showBackgroundHelp()
        })
    }

    private fun choosePace() {
        val modes = arrayOf("Balanced · fewer background checks",
            "Responsive · more frequent checks")
        val current = if (RecoveryPrefs.mode(this) == RecoveryPolicy.Mode.BALANCED) 0 else 1
        AlertDialog.Builder(this).setTitle("Recovery pace")
            .setSingleChoiceItems(modes, current) { dialog, selected ->
                val mode = if (selected == 0) "balanced" else "responsive"
                getSharedPreferences("profile", MODE_PRIVATE).edit()
                    .putString("recovery_mode", mode).commit()
                if (RecoveryPrefs.enabled(this)) RecoveryScheduler.schedulePeriodic(this)
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showBackgroundHelp() {
        val health = BackgroundHealth.inspect(this)
        val last = if (health.lastRun > 0)
            "\n\nLast background check: " +
                DateFormat.getDateTimeInstance(DateFormat.SHORT,
                    DateFormat.SHORT).format(Date(health.lastRun))
        else "\n\nNo completed background check recorded yet."
        val detail = when {
            !health.backgroundLocation ->
                "Rejoin needs Location access set to 'Allow all the time' to identify saved Wi-Fi while the app is closed."
            health.restricted || !health.batteryUnrestricted ->
                "Your phone may delay Rejoin in the background. Check Battery settings for Rejoin and allow background activity. On Motorola, look for 'Always allow' if available."
            else -> "Android schedules Rejoin's background checks. Deep sleep can still delay the next check."
        }
        AlertDialog.Builder(this).setTitle(health.title)
            .setMessage(detail + last)
            .setNegativeButton("Close", null)
            .setPositiveButton("App settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName)))
            }.show()
    }

    private fun confirmLogin() {
        AlertDialog.Builder(this).setTitle("Try one campus login?")
            .setMessage("This uses the login saved for your current Wi-Fi. The campus portal's HTTP connection isn't encrypted.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Sign in") { _, _ -> runTest("login") }.show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this).setTitle("Log out the shared connection?")
            .setMessage("Other devices on the same router may lose access until someone signs back in.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Log out") { _, _ ->
                getSharedPreferences("profile", MODE_PRIVATE).edit()
                    .putLong("manual_test_until",
                        System.currentTimeMillis() + 10 * 60_000L).commit()
                runTest("logout")
            }.show()
    }

    private fun confirmErase() {
        AlertDialog.Builder(this).setTitle("Erase saved passwords?")
            .setMessage("This turns off automatic recovery. Saved Wi-Fi networks will remain.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Erase") { _, _ ->
                RecoveryScheduler.disable(this)
                CredentialVault.clear(this)
                ActivityHistory.record(this, "All logins erased")
                refresh()
            }.show()
    }

    private fun runTest(kind: String) {
        worker.execute {
            val result = try {
                when (kind) {
                    "login" -> SessionEngine.run(applicationContext, userInitiated = true,
                        explicitlyLoggedOut = true)
                    "logout" -> LiveSessionTest.logout(applicationContext)
                    else -> InternetVerifier.check(applicationContext).let {
                        it.state.name + ": " + it.details
                    }
                }
            } catch (e: Exception) { "Test failed (" + e.javaClass.simpleName + ")" }
            runOnUiThread { message("Connection test", result) }
        }
    }

    private fun message(title: String, body: String) =
        AlertDialog.Builder(this).setTitle(title).setMessage(body)
            .setPositiveButton("OK", null).show()

    override fun onResume() {
        super.onResume()
        if (::networksRow.isInitialized) refresh()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
