package org.campus.autoconnect

import android.app.Activity
import android.content.Intent
import android.net.Uri

class SettingsActivity : Activity() {
    override fun onResume() {
        super.onResume()
        val body = RejoinUi.screen(this, "Settings")
        val networkCount = TrustedNetworks.all(this).size
        val loginCount = CredentialVault.list(this).size

        RejoinUi.section(body, "Your setup", 0)
        RejoinUi.add(body, RejoinUi.navRow(this, "Saved Wi-Fi", when (networkCount) {
            0 -> "No networks saved"
            1 -> "1 network saved"
            else -> "$networkCount networks saved"
        }, R.drawable.ic_wifi) { open(NetworksActivity::class.java) }, 4)
        RejoinUi.add(body, RejoinUi.rule(this))
        RejoinUi.add(body, RejoinUi.navRow(this, "Saved logins", when (loginCount) {
            0 -> "No logins saved"
            1 -> "1 login saved"
            else -> "$loginCount logins saved"
        }, R.drawable.ic_key) { open(LoginsActivity::class.java) })
        RejoinUi.add(body, RejoinUi.rule(this))
        val auto = BackgroundHealth.inspect(this)
        RejoinUi.add(body, RejoinUi.navRow(this, "Auto-reconnect", when (auto.level) {
            BackgroundHealth.Level.PAUSED -> "Off"
            BackgroundHealth.Level.NEEDS_ACTION -> "On · review background access"
            BackgroundHealth.Level.READY -> "On"
        }, R.drawable.ic_shield) { open(ProtectionActivity::class.java) })

        RejoinUi.section(body, "Help")
        RejoinUi.add(body, RejoinUi.navRow(this, "Troubleshooting",
            "Check internet or open campus sign-in", R.drawable.ic_activity) {
            open(DiagnosticsActivity::class.java)
        }, 4)
        RejoinUi.add(body, RejoinUi.rule(this))
        RejoinUi.add(body, RejoinUi.navRow(this, "EithonX on GitHub", "Developer",
            R.drawable.ic_external) {
            val url = Uri.parse("https://github.com/EithonX")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, url))
            } catch (_: android.content.ActivityNotFoundException) {
                android.app.AlertDialog.Builder(this).setTitle("EithonX on GitHub")
                    .setMessage(url.toString()).setPositiveButton("OK", null).show()
            }
        })

        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        RejoinUi.add(body, RejoinUi.label(this, "Rejoin $version", 13f, RejoinUi.muted), 24)
    }

    private fun open(activity: Class<out Activity>) = startActivity(Intent(this, activity))
}
