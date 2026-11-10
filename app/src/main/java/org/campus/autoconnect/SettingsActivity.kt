package org.campus.autoconnect

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Settings shows people what is saved, and hides technical diagnostics. */
class SettingsActivity : Activity() {
    private lateinit var networks: LinearLayout
    private lateinit var accounts: LinearLayout
    private lateinit var recovery: LinearLayout
    private lateinit var advanced: LinearLayout
    private lateinit var advancedLink: TextView
    private lateinit var backgroundTitle: TextView
    private lateinit var backgroundDescription: TextView
    private lateinit var backgroundLastRun: TextView
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = RejoinUi.column(this).apply { setBackgroundColor(RejoinUi.canvas) }
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val body = RejoinUi.column(this).apply {
            val d = RejoinUi.dp(this@SettingsActivity, 23)
            setPadding(d, RejoinUi.dp(this@SettingsActivity, 26), d, d)
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        setContentView(root)
        RejoinUi.applySystemBars(this, root)

        val bar = RejoinUi.row(this)
        bar.addView(RejoinUi.smallAction(this, "←") { finish() },
            LinearLayout.LayoutParams(RejoinUi.dp(this, 38), -2))
        bar.addView(RejoinUi.label(this, "Settings", 27f, RejoinUi.ink, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        RejoinUi.add(body, bar)
        RejoinUi.add(body, RejoinUi.label(this, "Everything you need. Nothing you don't.",
            13f, RejoinUi.muted), 9)

        heading(body, "TRUSTED WI-FI", 28)
        val netCard = RejoinUi.inset(this)
        RejoinUi.add(body, netCard, 12)
        RejoinUi.add(netCard, RejoinUi.label(this,
            "Only networks you approve can use saved logins.", 12f, RejoinUi.muted))
        networks = RejoinUi.column(this)
        RejoinUi.add(netCard, networks, 15)
        RejoinUi.add(netCard, RejoinUi.action(this, "Add the Wi-Fi I'm using", false) {
            saveCurrentNetwork()
        }, 16)

        heading(body, "SAVED LOGINS", 28)
        val loginCard = RejoinUi.inset(this)
        RejoinUi.add(body, loginCard, 12)
        RejoinUi.add(loginCard, RejoinUi.label(this,
            "One login is enough. Add another only if a network needs it.",
            12f, RejoinUi.muted))
        accounts = RejoinUi.column(this)
        RejoinUi.add(loginCard, accounts, 14)
        RejoinUi.add(loginCard, RejoinUi.action(this, "Manage saved logins", false) {
            startActivity(Intent(this, LoginsActivity::class.java))
        }, 16)

        heading(body, "AUTOMATIC RECOVERY", 28)
        val modeCard = RejoinUi.inset(this)
        RejoinUi.add(body, modeCard, 12)
        RejoinUi.add(modeCard, RejoinUi.label(this, "Recovery pace", 17f, RejoinUi.ink, true))
        RejoinUi.add(modeCard, RejoinUi.label(this,
            "Balanced saves battery. Responsive checks more often when Android allows.",
            12f, RejoinUi.muted), 8)
        recovery = RejoinUi.row(this)
        RejoinUi.add(modeCard, recovery, 16)
        RejoinUi.add(modeCard, RejoinUi.smallAction(this, "Background access settings  →") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + packageName)))
        }, 12)

        heading(body, "BACKGROUND ACCESS", 28)
        val bgCard = RejoinUi.inset(this)
        RejoinUi.add(body, bgCard, 12)
        backgroundTitle = RejoinUi.label(this, "Checking protection…", 16f, RejoinUi.ink, true)
        RejoinUi.add(bgCard, backgroundTitle)
        backgroundDescription = RejoinUi.label(this, "", 13f, RejoinUi.muted)
        RejoinUi.add(bgCard, backgroundDescription, 9)
        backgroundLastRun = RejoinUi.label(this, "", 12f, RejoinUi.muted)
        RejoinUi.add(bgCard, backgroundLastRun, 12)
        RejoinUi.add(bgCard, RejoinUi.action(this, "Review background access", false) {
            backgroundHelp()
        }, 17)

        heading(body, "ABOUT", 28)
        val aboutCard = RejoinUi.inset(this)
        RejoinUi.add(body, aboutCard, 12)
        RejoinUi.add(aboutCard, RejoinUi.label(this, "Rejoin", 18f, RejoinUi.ink, true))
        RejoinUi.add(aboutCard, RejoinUi.label(this,
            "A small Android utility that handles campus sign-in so you don't have to.",
            13f, RejoinUi.muted), 8)
        RejoinUi.add(aboutCard, RejoinUi.label(this, "Developed by EithonX",
            13f, RejoinUi.ink, true), 18)
        RejoinUi.add(aboutCard, RejoinUi.smallAction(this, "GitHub  ·  github.com/EithonX  ↗") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/EithonX")))
        }, 6)
        RejoinUi.add(aboutCard, RejoinUi.label(this,
            "Your saved logins stay encrypted on this phone. Rejoin has no analytics or cloud account.",
            12f, RejoinUi.muted), 12)
        RejoinUi.add(aboutCard, RejoinUi.label(this,
            "CU currently uses an unencrypted HTTP sign-in page. Automatic login is optional and carries an in-transit password risk.",
            12f, RejoinUi.amber), 9)

        heading(body, "ADVANCED", 28)
        val tools = RejoinUi.inset(this)
        RejoinUi.add(body, tools, 12)
        advancedLink = RejoinUi.smallAction(this, "Diagnostics and testing  +") {
            advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            advancedLink.text = if (advanced.visibility == View.VISIBLE)
                "Diagnostics and testing  −" else "Diagnostics and testing  +"
        }
        RejoinUi.add(tools, advancedLink)
        advanced = RejoinUi.column(this).apply { visibility = View.GONE }
        RejoinUi.add(tools, advanced, 10)
        RejoinUi.add(advanced, RejoinUi.label(this,
            "Testing logout can interrupt everyone sharing the router.",
            12f, RejoinUi.muted))
        RejoinUi.add(advanced, RejoinUi.action(this, "Test sign-in once", false) {
            confirmLogin()
        }, 12)
        RejoinUi.add(advanced, RejoinUi.action(this, "Log out shared session", false) {
            confirmLogout()
        }, 9)
        RejoinUi.add(advanced, RejoinUi.action(this, "Check internet", false) {
            runTest("check")
        }, 9)
        RejoinUi.add(advanced, RejoinUi.action(this, "Open CU sign-in page", false) {
            if (LiveSessionTest.selectedWifiMatches(this)) {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://172.16.2.1:1000/login?")))
            } else message("Not on trusted Wi-Fi", "Connect to a saved Wi-Fi network first.")
        }, 9)
        RejoinUi.add(advanced, RejoinUi.action(this, "Erase all saved logins", false) {
            confirmErase()
        }, 9)
        RejoinUi.add(body, RejoinUi.label(this, "REJOIN  ·  v2.1",
            11f, RejoinUi.muted).apply { gravity = Gravity.CENTER }, 27)
        refresh()
    }

    private fun heading(root: LinearLayout, label: String, top: Int) {
        RejoinUi.add(root, RejoinUi.label(this, label, 11f, RejoinUi.muted, true)
            .apply { letterSpacing = .13f }, top)
    }

    private fun currentProfile(): TrustedNetworks.Profile? =
        WifiProbe.current(this)?.let { TrustedNetworks.find(this, it) }

    private fun saveCurrentNetwork() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 52)
            return
        }
        val snapshot = WifiProbe.current(this) ?: run {
            message("Wi-Fi not found", "Connect to the router you want to save, then try again.")
            return
        }
        val existing = TrustedNetworks.find(this, snapshot)
        if (existing != null) {
            startActivity(Intent(this, NetworkDetailsActivity::class.java)
                .putExtra(NetworkDetailsActivity.EXTRA_ID, existing.id))
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Trust " + snapshot.ssid + "?")
            .setMessage("Rejoin will recognize this router and use the default saved login. You can change its login later.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save Wi-Fi") { _, _ ->
                val stored = TrustedNetworks.saveCurrent(this, snapshot)
                if (stored == null) message("Couldn't save Wi-Fi",
                    "You may have reached the saved network limit.")
                else {
                    ActivityHistory.record(this, "Trusted Wi-Fi added")
                    refresh()
                }
            }.show()
    }

    private fun rebuildNetworks() {
        networks.removeAllViews()
        val profiles = TrustedNetworks.all(this)
        val current = currentProfile()
        val accountsList = CredentialVault.list(this)
        if (profiles.isEmpty()) {
            RejoinUi.add(networks, RejoinUi.label(this, "No Wi-Fi saved yet",
                15f, RejoinUi.ink, true), 6)
            RejoinUi.add(networks, RejoinUi.label(this,
                "Connect to a campus router and add it here.",
                12f, RejoinUi.muted), 5)
            return
        }
        for ((index, profile) in profiles.withIndex()) {
            if (index != 0) RejoinUi.add(networks, RejoinUi.rule(this), 13)
            val row = RejoinUi.row(this)
            val label = RejoinUi.column(this)
            row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            RejoinUi.add(label, RejoinUi.label(this,
                profile.label + if (current?.id == profile.id) "  ·  Here" else "",
                15f, RejoinUi.ink, true))
            val assigned = accountsList.firstOrNull { it.id == profile.loginId }
            val name = assigned?.label ?: if (profile.loginId == null) "Default login" else "Login missing"
            RejoinUi.add(label, RejoinUi.label(this, name, 12f, RejoinUi.muted), 6)
            row.addView(RejoinUi.smallAction(this, "Manage  ›") {
                startActivity(Intent(this, NetworkDetailsActivity::class.java)
                    .putExtra(NetworkDetailsActivity.EXTRA_ID, profile.id))
            })
            RejoinUi.add(networks, row, 13)
        }
    }

    private fun rebuildLogins() {
        accounts.removeAllViews()
        val all = CredentialVault.list(this)
        if (all.isEmpty()) {
            RejoinUi.add(accounts, RejoinUi.label(this, "No password saved",
                15f, RejoinUi.ink, true), 7)
            RejoinUi.add(accounts, RejoinUi.label(this,
                "Save a login once and Rejoin can reuse it for all your trusted routers.",
                12f, RejoinUi.muted), 6)
        } else {
            val default = all.firstOrNull { it.id == CredentialVault.defaultId(this) }
            RejoinUi.add(accounts, RejoinUi.label(this,
                "✓  Password saved on this device", 15f, RejoinUi.jade, true), 7)
            RejoinUi.add(accounts, RejoinUi.label(this,
                all.size.toString() +
                    (if (all.size == 1) " saved login" else " saved logins") +
                    "  ·  Default: " + (default?.label ?: "Not set"), 12f, RejoinUi.muted), 5)
        }
    }

    private fun rebuildModes() {
        recovery.removeAllViews()
        val current = RecoveryPrefs.mode(this)
        for ((i, mode) in listOf(RecoveryPolicy.Mode.BALANCED, RecoveryPolicy.Mode.RESPONSIVE).withIndex()) {
            val selected = mode == current
            val button = RejoinUi.action(this,
                if (mode == RecoveryPolicy.Mode.BALANCED) "Balanced" else "Responsive", selected) {
                getSharedPreferences("profile", MODE_PRIVATE).edit()
                    .putString("recovery_mode", mode.name.lowercase()).commit()
                if (RecoveryPrefs.enabled(this)) RecoveryScheduler.schedule(this, 60_000L)
                rebuildModes()
            }
            recovery.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (i == 0) rightMargin = RejoinUi.dp(this@SettingsActivity, 5)
                else leftMargin = RejoinUi.dp(this@SettingsActivity, 5)
            })
        }
    }

    private fun confirmLogin() {
        AlertDialog.Builder(this).setTitle("Try one campus sign-in?")
            .setMessage("Uses the saved login assigned to the Wi-Fi you're on.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Sign in") { _, _ -> runTest("login") }.show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this).setTitle("Disconnect the shared connection?")
            .setMessage("Other people connected to this router may lose internet until someone signs back in.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Log out") { _, _ ->
                getSharedPreferences("profile", MODE_PRIVATE).edit()
                    .putLong("manual_test_until", System.currentTimeMillis() + 10 * 60_000L).commit()
                runTest("logout")
            }.show()
    }

    private fun runTest(kind: String) {
        worker.execute {
            val result = try {
                when (kind) {
                    "login" -> SessionEngine.run(applicationContext, userInitiated = true,
                        explicitlyLoggedOut = true)
                    "logout" -> LiveSessionTest.logout(applicationContext)
                    else -> {
                        val state = InternetVerifier.check(applicationContext)
                        state.state.name + ": " + state.details
                    }
                }
            } catch (e: Exception) { "Could not complete (" + e.javaClass.simpleName + ")" }
            runOnUiThread { message("Connection test", result) }
        }
    }

    private fun confirmErase() {
        AlertDialog.Builder(this).setTitle("Erase every saved password?")
            .setMessage("Automatic recovery will turn off. Trusted Wi-Fi entries will remain, but no login will be saved.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Erase all") { _, _ ->
                RecoveryScheduler.disable(this)
                CredentialVault.clear(this)
                ActivityHistory.record(this, "All logins cleared")
                refresh()
            }.show()
    }

    private fun message(title: String, detail: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(detail)
            .setPositiveButton("OK", null).show()
    }

    private fun refresh() {
        if (!::networks.isInitialized) return
        rebuildNetworks()
        rebuildLogins()
        rebuildModes()
        if (::backgroundTitle.isInitialized) updateBackgroundHealth()
    }

    private fun updateBackgroundHealth() {
        val health = BackgroundHealth.inspect(this)
        backgroundTitle.text = health.title
        backgroundDescription.text = health.description
        val last = health.lastRun
        backgroundLastRun.text = if (last <= 0L) {
            "No background check recorded yet."
        } else {
            "Last background check: " +
                java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                    .format(java.util.Date(last))
        }
    }

    private fun backgroundHelp() {
        val health = BackgroundHealth.inspect(this)
        val note = when {
            !health.backgroundLocation ->
                "1. Open Rejoin's app permissions.\n2. Open Location.\n3. Choose 'Allow all the time'.\n\n" +
                "Rejoin uses this only to recognize saved Wi-Fi while closed."
            health.restricted || !health.batteryUnrestricted ->
                "On Motorola, open Settings → Battery → Manage background apps → Rejoin " +
                "and choose 'Always allow' if available. Also check Rejoin's Battery settings " +
                "for unrestricted background activity.\n\nAndroid may still defer work during deep sleep."
            else ->
                "Rejoin's periodic Android job is installed. Checks are battery-managed " +
                "and may run later during Doze. If automatic recovery stops, review Battery " +
                "and Location permissions here."
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(health.title)
            .setMessage(note)
            .setNegativeButton("Close", null)
            .setPositiveButton("Open app settings") { _, _ ->
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + packageName)))
            }.show()
    }

    override fun onResume() {
        super.onResume()
        if (::networks.isInitialized) refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
        permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 52 && results.isNotEmpty() &&
            results[0] == PackageManager.PERMISSION_GRANTED) saveCurrentNetwork()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
