package com.eithonx.rejoin

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class SetupActivity : RejoinActivity() {
    private val prefs by lazy { getSharedPreferences("profile", MODE_PRIVATE) }
    private var revision by mutableIntStateOf(0)

    @Composable
    override fun Screen() {
        val profiles = remember(revision) { TrustedNetworks.all(this) }
        val hasLogin = remember(revision) { CredentialVault.hasCredentials(this) }
        val step = SetupFlow.next(profiles.isNotEmpty(), hasLogin, prefs.getBoolean("http_opt_in", false))
        Page("Set up Rejoin") {
            Text("Step ${step.ordinal + 1} of 3", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress = { (step.ordinal + 1) / 3f }, Modifier.fillMaxWidth())
            when (step) {
                SetupFlow.Step.WIFI -> WifiStep()
                SetupFlow.Step.LOGIN -> {
                    ScreenIntro(if (hasLogin) "Allow sign-in" else "Save your login",
                        if (hasLogin) "Choose whether Rejoin can use your saved login."
                        else "Enter the username and password you use on the Wi-Fi sign-in page.")
                    LoginForm(consentOnly = hasLogin) {
                        ActivityHistory.record(this, if (hasLogin) "Sign-in permission saved" else "Login saved")
                        revision++
                    }
                }
                SetupFlow.Step.READY -> ReadyStep(profiles)
            }
            if (step != SetupFlow.Step.READY) PageButton("Not now", filled = false) {
                prefs.edit().putBoolean("onboarding_deferred", true).apply()
                finish()
            }
        }
    }

    @Composable
    private fun WifiStep() {
        val wifi = remember(revision) { WifiProbe.current(this) }
        ScreenIntro("Save your Wi-Fi")
        when {
            !hasWifiAccess() -> {
                BodyText("Android requires precise location permission to identify your Wi-Fi.")
                PageButton("Allow Wi-Fi access") { requestWifiAccess() }
            }
            !WifiProbe.locationEnabled(this) -> {
                BodyText("Turn on Location so Rejoin can identify your Wi-Fi.")
                PageButton("Open Location settings") {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }
            wifi == null -> {
                BodyText("Connect to the Wi-Fi you want to save, then come back here.")
                PageButton("Open Wi-Fi settings") { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            }
            else -> {
                ListGroup {
                    Text(wifi.ssid, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                }
                PageButton("Save this Wi-Fi") {
                    when {
                        WifiProbe.current(this) != wifi -> {
                            revisionChanged()
                            notice("Wi-Fi changed", "Check the network name before saving.")
                        }
                        TrustedNetworks.saveCurrent(this, wifi) == null ->
                            notice("Couldn’t save Wi-Fi", "Try again or remove an unused network.")
                        else -> {
                            ActivityHistory.record(this, "Wi-Fi saved")
                            revisionChanged()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ReadyStep(profiles: List<TrustedNetworks.Profile>) {
        val enabled = RecoveryPrefs.enabled(this)
        val canSignIn = profiles.any { CredentialVault.hasLoginFor(this, it) }
        when {
            !canSignIn -> {
                ScreenIntro("Choose a login", "Assign a saved login to your Wi-Fi.")
                PageButton("Manage saved Wi-Fi") { open(NetworksActivity::class.java) }
            }
            enabled -> {
                ScreenIntro("Auto sign-in is on", "Rejoin can sign in again when your session expires.")
                PageButton("Done", action = ::finishSetup)
            }
            else -> {
                ScreenIntro("Turn on auto sign-in?", "Rejoin can sign in again when your Wi-Fi session expires.")
                PageButton("Turn on auto sign-in") {
                    if (RecoveryPrefs.enable(this)) finishSetup()
                    else notice("Couldn’t start auto sign-in", "Try again or finish setup with auto sign-in off.")
                }
                PageButton("Keep it off", filled = false, action = ::finishSetup)
            }
        }
    }

    private fun revisionChanged() { revision++ }

    private fun finishSetup() {
        prefs.edit().putBoolean("onboarding_completed", true)
            .putBoolean("onboarding_deferred", false).apply()
        finish()
    }

    override fun onResume() {
        super.onResume()
        revisionChanged()
    }

    override fun onWifiPermissionResult() {
        revisionChanged()
    }

}
