package com.eithonx.rejoin

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

class SetupActivity : RejoinActivity() {
    private data class Stage(val step: SetupFlow.Step, val hasLogin: Boolean)

    private val prefs by lazy { getSharedPreferences("profile", MODE_PRIVATE) }
    private var revision by mutableIntStateOf(0)

    @Composable
    override fun Screen() {
        val profiles = remember(revision) { TrustedNetworks.all(this) }
        val hasLogin = remember(revision) { CredentialVault.hasCredentials(this) }
        val step = SetupFlow.next(profiles.isNotEmpty(), hasLogin, prefs.getBoolean("http_opt_in", false))
        val scroll = rememberScrollState()
        LaunchedEffect(step) { scroll.scrollTo(0) }
        BackHandler(onBack = ::deferSetup)
        RejoinPage("Set up Rejoin", onBack = ::deferSetup, scroll = scroll) {
            SetupProgress(step)
            AnimatedContent(
                targetState = Stage(step, hasLogin),
                contentKey = { it.step },
                transitionSpec = {
                    (fadeIn(tween(200, delayMillis = 70)) +
                        slideInVertically(tween(240)) { it / 18 }) togetherWith fadeOut(tween(100))
                },
                label = "Setup step"
            ) { current ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                    if (!keyboardVisible) SetupArtwork(current.step)
                    when (current.step) {
                        SetupFlow.Step.WIFI -> WifiStep()
                        SetupFlow.Step.LOGIN -> {
                            ScreenIntro(if (current.hasLogin) "Allow sign-in" else "Save your login",
                                if (current.hasLogin) "Choose whether Rejoin can use your saved login."
                                else "Use the details from your Wi-Fi sign-in page.")
                            LoginForm(consentOnly = current.hasLogin) {
                                ActivityHistory.record(this@SetupActivity, if (current.hasLogin) "Sign-in permission saved" else "Login saved")
                                revision++
                            }
                        }
                        SetupFlow.Step.READY -> ReadyStep(profiles)
                    }
                }
            }
            if (step != SetupFlow.Step.READY) PageButton("Not now", filled = false, action = ::deferSetup)
        }
    }

    @Composable
    private fun SetupProgress(step: SetupFlow.Step) {
        val colors = MaterialTheme.colorScheme
        val label = when (step) {
            SetupFlow.Step.WIFI -> "Wi-Fi"
            SetupFlow.Step.LOGIN -> "Login"
            SetupFlow.Step.READY -> "Auto sign-in"
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text("${step.ordinal + 1} of 3", style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SetupFlow.Step.entries.forEach { item ->
                    Box(Modifier.weight(1f).height(3.dp).background(
                        if (item <= step) colors.primary else colors.outlineVariant,
                        MaterialTheme.shapes.extraSmall
                    ))
                }
            }
        }
    }

    @Composable
    private fun SetupArtwork(step: SetupFlow.Step) {
        val scene = when (step) {
            SetupFlow.Step.WIFI -> RelayScene.WAITING
            SetupFlow.Step.LOGIN -> RelayScene.LOGIN
            SetupFlow.Step.READY -> RelayScene.CONNECTED
        }
        Surface(shape = MaterialTheme.shapes.extraLarge, color = relayBackground) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RelayArtwork(scene, Modifier.size(width = 210.dp, height = 116.dp))
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
        val health = remember(revision) { BackgroundHealth.inspect(this) }
        val canSignIn = profiles.any { CredentialVault.hasLoginFor(this, it) }
        when {
            !canSignIn -> {
                ScreenIntro("Choose a login", "Assign a saved login to your Wi-Fi.")
                PageButton("Manage saved Wi-Fi") { open(NetworksActivity::class.java) }
            }
            health.enabled -> {
                val issue = health.issue?.takeIf { it.required }
                if (issue != null) {
                    ScreenIntro("Finish background setup")
                    PermissionNotice(issue) { resolveBackgroundIssue(issue) }
                    PageButton("Do this later", filled = false, action = ::finishSetup)
                } else {
                    ScreenIntro("You’re all set")
                    PageButton("Done", action = ::finishSetup)
                }
            }
            else -> {
                ScreenIntro("You’re ready to sign in", "Turn on auto sign-in to reconnect when your session expires.")
                PageButton("Turn on auto sign-in") {
                    if (RecoveryPrefs.enable(this)) revisionChanged()
                    else notice("Couldn’t start auto sign-in", "Try again or finish setup with auto sign-in off.")
                }
                PageButton("Keep it off", filled = false, action = ::finishSetup)
            }
        }
    }

    private fun revisionChanged() { revision++ }

    private fun deferSetup() {
        prefs.edit().putBoolean("onboarding_deferred", true).apply()
        finish()
    }

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
