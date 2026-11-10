package org.campus.autoconnect

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

class NetworksActivity : Activity() {
    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val body = RejoinUi.screen(this, "Saved Wi-Fi")
        val wifi = WifiProbe.current(this)
        val profiles = TrustedNetworks.all(this)
        val active = wifi?.let { TrustedNetworks.find(this, it) }

        if (profiles.isEmpty()) {
            RejoinUi.add(body, RejoinUi.label(this, "Save your campus Wi-Fi", 25f, bold = true))
            RejoinUi.add(body, RejoinUi.label(this,
                "Rejoin uses your saved login only on Wi-Fi you add here.", 15f, RejoinUi.muted), 6)
        } else {
            val logins = CredentialVault.list(this)
            profiles.forEachIndexed { index, profile ->
                if (index > 0) RejoinUi.add(body, RejoinUi.rule(this))
                val login = logins.find { it.id == profile.loginId }
                val detail = when {
                    active?.id == profile.id -> "Connected now"
                    login != null -> "Uses ${login.label}"
                    profile.loginId != null -> "Assigned login missing"
                    logins.isEmpty() -> "Login needed"
                    else -> "Uses default login"
                }
                RejoinUi.add(body, RejoinUi.navRow(this, profile.label, detail, R.drawable.ic_wifi) {
                    openDetails(profile)
                })
            }
        }

        when {
            !RejoinUi.hasWifiAccess(this) -> RejoinUi.add(body,
                RejoinUi.action(this, "Allow Wi-Fi access") { RejoinUi.requestWifiAccess(this, WIFI_PERMISSION) }, 16)
            !WifiProbe.locationEnabled(this) -> RejoinUi.add(body,
                RejoinUi.action(this, "Open Location settings") {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }, 16)
            wifi == null || active != null -> RejoinUi.add(body,
                RejoinUi.action(this, "Open Wi-Fi settings", filled = profiles.isEmpty()) {
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }, 16)
            else -> RejoinUi.add(body, RejoinUi.action(this, "Save connected Wi-Fi") {
                confirmSave(wifi)
            }, 16)
        }
        if (active != null) RejoinUi.add(body, RejoinUi.label(this,
            "To add another Wi-Fi, connect to it in Android settings first.", 14f, RejoinUi.muted), 8)
    }

    private fun confirmSave(wifi: WifiProbe.Snapshot) {
        AlertDialog.Builder(this).setTitle("Save ${wifi.ssid}?")
            .setMessage("Rejoin can use your saved login on this specific Wi-Fi access point.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save Wi-Fi") { _, _ ->
                val message = if (WifiProbe.current(this) != wifi) {
                    "Your Wi-Fi changed. Check the network before saving."
                } else if (TrustedNetworks.saveCurrent(this, wifi) == null) {
                    "Couldn’t save this Wi-Fi. Check your saved networks and try again."
                } else {
                    ActivityHistory.record(this, "Trusted network saved")
                    "Wi-Fi saved"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                render()
            }.show()
    }

    private fun openDetails(profile: TrustedNetworks.Profile) {
        startActivity(Intent(this, NetworkDetailsActivity::class.java)
            .putExtra(NetworkDetailsActivity.EXTRA_ID, profile.id))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WIFI_PERMISSION) {
            render()
            if (!RejoinUi.hasWifiAccess(this)) RejoinUi.explainWifiAccess(this)
        }
    }

    companion object {
        private const val WIFI_PERMISSION = 31
    }
}
