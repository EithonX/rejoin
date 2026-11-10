package com.eithonx.rejoin

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class NetworksActivity : RejoinActivity() {
    private var revision by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        revision++
    }

    @Composable
    override fun Screen() {
        val wifi = remember(revision) { WifiProbe.current(this) }
        val profiles = remember(revision) { TrustedNetworks.all(this) }
        val logins = remember(revision) { CredentialVault.list(this) }
        val active = wifi?.let { snap -> profiles.firstOrNull { TrustedNetworks.matches(it, snap.ssid, snap.bssid) } }
        Page("Saved Wi-Fi") {
            if (profiles.isEmpty()) {
                ScreenIntro("Add your first network", "Rejoin uses your saved login on networks you add here.")
            }
            when {
                !hasWifiAccess() -> PageButton("Allow Wi-Fi access") { requestWifiAccess() }
                !WifiProbe.locationEnabled(this) -> PageButton("Open Location settings") {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                wifi != null && active == null -> PageButton("Save ${wifi.ssid}") { confirmSave(wifi) }
                else -> PageButton("Open Wi-Fi settings", filled = profiles.isEmpty()) {
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }
            }
            if (profiles.isNotEmpty()) ListGroup {
                profiles.forEachIndexed { index, profile ->
                    if (index > 0) HorizontalDivider()
                    val login = logins.find { it.id == profile.loginId }
                    val detail = when {
                        active?.id == profile.id -> "Connected now"
                        login != null -> "Uses ${login.label}"
                        profile.loginId != null -> "Assigned login missing"
                        logins.isEmpty() -> "Login needed"
                        else -> "Uses default login"
                    }
                    ActionRow(profile.label, detail, R.drawable.ic_wifi) {
                        startActivity(Intent(this@NetworksActivity, NetworkDetailsActivity::class.java)
                            .putExtra(NetworkDetailsActivity.EXTRA_ID, profile.id))
                    }
                }
            }
        }
    }

    private fun confirmSave(wifi: WifiProbe.Snapshot) {
        confirm("Save ${wifi.ssid}?", "Rejoin can use your login on this specific access point.",
            "Save Wi-Fi", "Cancel") {
            when {
                WifiProbe.current(this) != wifi ->
                    notice("Wi-Fi changed", "Check the network name before saving.")
                TrustedNetworks.saveCurrent(this, wifi) == null ->
                    notice("Couldn’t save Wi-Fi", "Try again or remove an unused network.")
                else -> {
                    ActivityHistory.record(this, "Wi-Fi saved")
                    Toast.makeText(this, "Wi-Fi saved", Toast.LENGTH_SHORT).show()
                }
            }
            revision++
        }
    }

    override fun onWifiPermissionResult() {
        revision++
    }

}
