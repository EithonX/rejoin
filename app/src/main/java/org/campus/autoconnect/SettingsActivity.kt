package org.campus.autoconnect

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Settings, not a sales page: short rows, clear outcomes, advanced tests hidden.
 */
class SettingsActivity : Activity() {
    private lateinit var networksRow: LinearLayout
    private lateinit var accountsRow: LinearLayout
    private lateinit var protectionRow: LinearLayout
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

        RejoinUi.section(body, "Networks & accounts", 29)
        val setupGroup = RejoinUi.inset(this)
        RejoinUi.add(body, setupGroup, 12)
        networksRow = RejoinUi.column(this)
        accountsRow = RejoinUi.column(this)
        RejoinUi.add(setupGroup, networksRow)
        RejoinUi.add(setupGroup, RejoinUi.rule(this), 4)
        RejoinUi.add(setupGroup, accountsRow)

        RejoinUi.section(body, "Connection", 29)
        val recoveryGroup = RejoinUi.inset(this)
        RejoinUi.add(body, recoveryGroup, 12)
        protectionRow = RejoinUi.column(this)
        RejoinUi.add(recoveryGroup, protectionRow)

        RejoinUi.section(body, "More", 29)
        val moreGroup = RejoinUi.inset(this)
        RejoinUi.add(body, moreGroup, 12)
        RejoinUi.add(moreGroup, RejoinUi.navRow(this,
            "Activity", "Recent connection events") {
            startActivity(Intent(this, ActivityLogActivity::class.java))
        })
        RejoinUi.add(moreGroup, RejoinUi.rule(this), 4)
        RejoinUi.add(moreGroup, RejoinUi.navRow(this, "Developer", "EithonX on GitHub") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/EithonX")))
        })

        RejoinUi.section(body, "Support", 29)
        val support = RejoinUi.inset(this)
        RejoinUi.add(body, support, 12)
        support.addView(RejoinUi.navRow(this, "Troubleshooting",
            "Connection checks and advanced options") {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        })

        RejoinUi.add(body, RejoinUi.label(this,
            "Rejoin 2.3 beta   ·   Made by EithonX", 12f, RejoinUi.muted), 32)
        refresh()
    }

    private fun refresh() {
        if (!::networksRow.isInitialized) return
        networksRow.removeAllViews()
        accountsRow.removeAllViews()
        protectionRow.removeAllViews()
        val profiles = TrustedNetworks.all(this)
        val logins = CredentialVault.list(this)
        setupCaption.text = if (profiles.isEmpty() || logins.isEmpty())
            "Just two things to get started: Wi-Fi and a login."
        else "Your saved networks and logins."

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
        val health = BackgroundHealth.inspect(this)
        val protectionSummary = when (health.level) {
            BackgroundHealth.Level.READY -> "On"
            BackgroundHealth.Level.PAUSED -> "Off"
            BackgroundHealth.Level.NEEDS_ACTION -> "On · needs attention"
        }
        protectionRow.addView(RejoinUi.navRow(this,
            "Auto-reconnect", protectionSummary) {
            startActivity(Intent(this, ProtectionActivity::class.java))
        })
    }

    override fun onResume() {
        super.onResume()
        if (::networksRow.isInitialized) refresh()
    }


}
