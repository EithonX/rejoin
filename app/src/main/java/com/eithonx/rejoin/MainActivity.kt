package com.eithonx.rejoin

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : RejoinActivity() {
    private data class Reading(
        val wifi: WifiProbe.Snapshot,
        val state: InternetVerifier.State,
        val failedSignIn: Boolean = false
    )

    private val ui = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val prefs by lazy { getSharedPreferences("profile", MODE_PRIVATE) }
    private var monitor: ConnectivityManager.NetworkCallback? = null
    private var pendingRecovery: Runnable? = null
    private var lastPortalAttempt: Long? = null
    private var reading: Reading? = null
    private var systemReading: Reading? = null
    private var runningCheck: WifiProbe.Snapshot? = null
    private var signingIn = false

    private var home by mutableStateOf(HomeUi())
    private var needsLoginFor: TrustedNetworks.Profile? = null

    @Composable
    override fun Screen() {
        HomeScreen(home, ReleaseUpdates.available?.version, onSettings = { open(SettingsActivity::class.java) },
            onAutomation = {
                val profile = needsLoginFor
                if (profile != null) openLoginFor(profile) else open(ProtectionActivity::class.java)
            },
            onActivity = { open(ActivityLogActivity::class.java) },
            onIssue = ::resolveBackgroundIssue,
            onUpdate = { open(AboutActivity::class.java) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val setUp = TrustedNetworks.all(this).isNotEmpty() && CredentialVault.hasCredentials(this) &&
            prefs.getBoolean("http_opt_in", false)
        if (setUp) prefs.edit().putBoolean("onboarding_completed", true).apply()
        if (savedInstanceState == null && SetupFlow.shouldAutoOpen(
                setUp, prefs.getBoolean("onboarding_completed", false),
                prefs.getBoolean("onboarding_deferred", false))) {
            open(SetupActivity::class.java)
        }
    }

    private fun render() {
        val wifi = WifiProbe.current(this)
        val profile = wifi?.let { TrustedNetworks.find(this, it) }
        val hasProfiles = TrustedNetworks.all(this).isNotEmpty()
        val permitted = hasWifiAccess()
        val locationOn = WifiProbe.locationEnabled(this)
        val hasAccount = profile?.let { CredentialVault.hasLoginFor(this, it) } == true
        val consent = prefs.getBoolean("http_opt_in", false)
        val hasAnyAccount = hasAccount || (profile == null && CredentialVault.hasCredentials(this))
        val health = BackgroundHealth.inspect(this)
        val currentReading = reading?.takeIf { it.wifi == wifi }
        val checkedAt = prefs.getLong("last_checked", 0L)
        val checkedHere = profile != null && profile.id == prefs.getString("last_profile_id", null)
        val fresh = checkedHere && checkedAt > 0 &&
            System.currentTimeMillis() - checkedAt in 0L..120_000L
        val androidState = wifi?.let { systemState(it.network) }
        val state = currentReading?.state ?: when {
            wifi == null || profile == null -> InternetVerifier.State.UNKNOWN
            androidState == InternetVerifier.State.PORTAL -> InternetVerifier.State.PORTAL
            androidState == InternetVerifier.State.ONLINE -> InternetVerifier.State.ONLINE
            fresh && prefs.getString("last_class", null) in listOf("healthy", "restored") ->
                InternetVerifier.State.ONLINE
            fresh && prefs.getString("last_class", null) == "portal" -> InternetVerifier.State.PORTAL
            else -> InternetVerifier.State.UNKNOWN
        }

        needsLoginFor = profile?.takeIf { !hasAccount }
        val automation = when {
            !hasProfiles || !consent || !hasAnyAccount -> "Setup needed"
            !health.enabled -> "Off"
            health.issue?.required == true -> "Needs attention"
            else -> "On"
        }
        val lastChecked = if (checkedHere && checkedAt > 0)
            "Checked " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(checkedAt)) else null

        val status = when {
            !hasProfiles -> HomeStatus("Add your Wi-Fi",
                "Save a network and its login to sign in with Rejoin.", actionLabel = "Set up Rejoin") {
                open(SetupActivity::class.java)
            }
            !permitted -> HomeStatus("Wi-Fi access needed",
                "Allow precise location so Rejoin can identify your Wi-Fi.", ConnectionTone.ATTENTION,
                "Allow Wi-Fi access") { requestWifiAccess() }
            !locationOn -> HomeStatus("Location is off",
                "Turn on Location to identify your Wi-Fi.", ConnectionTone.ATTENTION,
                "Open Location settings") { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            wifi == null -> HomeStatus("Waiting for Wi-Fi",
                "Connect to a saved network to check your connection.", actionLabel = "Open Wi-Fi settings") {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            profile == null -> HomeStatus("Unsaved Wi-Fi",
                "Save this network to use Rejoin here.", actionLabel = "Review this network") {
                open(NetworksActivity::class.java)
            }
            runningCheck == wifi -> HomeStatus(if (signingIn) "Signing in…" else "Checking connection…",
                tone = ConnectionTone.WORKING)
            state == InternetVerifier.State.ONLINE -> HomeStatus("Online",
                tone = ConnectionTone.ONLINE, actionLabel = "Check connection") { runCheck(wifi) }
            !hasAccount -> HomeStatus("Login needed",
                "Choose the login you use on this Wi-Fi.", ConnectionTone.ATTENTION, "Set up login") {
                openLoginFor(profile)
            }
            !consent -> HomeStatus("Sign-in permission needed",
                "Review how Rejoin uses your saved login.", ConnectionTone.ATTENTION, "Review sign-in") {
                open(SetupActivity::class.java)
            }
            currentReading?.failedSignIn == true -> HomeStatus("Couldn’t sign in",
                "Open the sign-in page to reconnect.", ConnectionTone.ERROR, "Open sign-in page",
                ::openSignInPage)
            state == InternetVerifier.State.PORTAL -> HomeStatus("Sign-in required",
                "This Wi-Fi is asking you to sign in.", ConnectionTone.ATTENTION, "Sign in") {
                runCheck(wifi, repair = true)
            }
            else -> HomeStatus("Connection unverified",
                actionLabel = "Check connection") { runCheck(wifi) }
        }
        val issue = health.issue?.takeIf {
            it.required && it != BackgroundHealth.Issue.PRECISE_LOCATION && it != BackgroundHealth.Issue.LOCATION_OFF
        }
        home = HomeUi(status, profile?.label ?: wifi?.ssid, automation, lastChecked, issue)
    }

    private fun openLoginFor(profile: TrustedNetworks.Profile) {
        if (profile.loginId == null) {
            open(LoginEditorActivity::class.java)
        } else {
            startActivity(Intent(this, NetworkDetailsActivity::class.java)
                .putExtra(NetworkDetailsActivity.EXTRA_ID, profile.id))
        }
    }

    private fun runCheck(wifi: WifiProbe.Snapshot, repair: Boolean = false, automatic: Boolean = false) {
        if (runningCheck != null) return
        if (WifiProbe.current(this) != wifi) {
            render()
            return
        }
        val profile = TrustedNetworks.find(this, wifi) ?: return
        runningCheck = wifi
        signingIn = repair
        render()
        executor.execute {
            val result = try {
                if (repair) reconnect(wifi, profile.id, automatic) else checkInternet(wifi, profile.id)
            } catch (_: RuntimeException) {
                Reading(wifi, InternetVerifier.State.UNKNOWN, failedSignIn = repair && !automatic)
            }
            ui.post {
                runningCheck = null
                if (!isDestroyed) {
                    if (WifiProbe.current(this) == wifi) reading = result
                    render()
                }
            }
        }
    }

    private fun reconnect(wifi: WifiProbe.Snapshot, profileId: String, automatic: Boolean): Reading {
        val session = SessionEngine.run(applicationContext, userInitiated = !automatic,
            requiredNetwork = wifi.network, requiredProfileId = profileId)
        val online = session.outcome == RecoveryPolicy.Outcome.HEALTHY ||
            session.outcome == RecoveryPolicy.Outcome.RECOVERED
        val state = if (online) InternetVerifier.State.ONLINE else systemState(wifi.network)
        return Reading(wifi, state, failedSignIn = !online && !automatic && !session.busy)
    }

    private fun checkInternet(wifi: WifiProbe.Snapshot, profileId: String): Reading {
        val check = InternetVerifier.check(applicationContext, wifi.network)
        if (WifiProbe.current(applicationContext) == wifi) {
            prefs.edit()
                .putLong("last_checked", System.currentTimeMillis())
                .putString("last_profile_id", profileId)
                .putString("last_result", check.details)
                .putString("last_class", when (check.state) {
                    InternetVerifier.State.ONLINE -> "healthy"
                    InternetVerifier.State.PORTAL -> "portal"
                    InternetVerifier.State.UNKNOWN -> "unknown"
                }).apply()
            if (check.state == InternetVerifier.State.ONLINE) {
                NetworkStatusHint.requestAfterVerifiedOnline(applicationContext)
            }
        }
        return Reading(wifi, check.state)
    }

    private fun systemState(network: Network): InternetVerifier.State {
        val caps = connectivity.getNetworkCapabilities(network)
        return when {
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true ->
                InternetVerifier.State.PORTAL
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true ->
                InternetVerifier.State.ONLINE
            else -> InternetVerifier.State.UNKNOWN
        }
    }

    override fun onStart() {
        super.onStart()
        reading = null
        systemReading = null
        if (RecoveryPrefs.enabled(this)) {
            RecoveryScheduler.schedulePeriodic(this)
            RecoveryScheduler.registerWifiEvents(this)
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                ui.post {
                    if (monitor !== this) return@post
                    val wifi = WifiProbe.current(this@MainActivity)
                    if (wifi?.network == network && TrustedNetworks.find(this@MainActivity, wifi) != null) {
                        val state = systemState(network)
                        val previous = systemReading
                        val observed = Reading(wifi, state)
                        if (observed != previous) {
                            systemReading = observed
                            val failedSignIn = reading?.let { it.wifi == wifi && it.failedSignIn } == true
                            if (!failedSignIn || state == InternetVerifier.State.ONLINE) {
                                if (state != InternetVerifier.State.UNKNOWN || previous?.wifi == wifi) {
                                    reading = observed
                                }
                            }
                        }
                        if (state == InternetVerifier.State.PORTAL) schedulePortalRecovery(wifi)
                        else cancelPortalRecovery()
                    }
                    render()
                }
            }

            override fun onLost(network: Network) {
                ui.post {
                    if (monitor !== this) return@post
                    if (reading?.wifi?.network == network) reading = null
                    if (systemReading?.wifi?.network == network) {
                        systemReading = null
                        cancelPortalRecovery()
                    }
                    render()
                }
            }
        }
        val request = RecoveryScheduler.wifiRequest()
        try {
            connectivity.registerNetworkCallback(request, callback)
            monitor = callback
        } catch (_: RuntimeException) {
            // The foreground check is still available if Android refuses the callback.
        }
        val wifi = WifiProbe.current(this)
        val profile = wifi?.let { TrustedNetworks.find(this, it) }
        if (wifi != null && profile != null && systemState(wifi.network) == InternetVerifier.State.UNKNOWN &&
            (prefs.getString("last_profile_id", null) != profile.id ||
                System.currentTimeMillis() - prefs.getLong("last_checked", 0L) > 120_000L)) {
            ui.postDelayed({
                if (monitor === callback && WifiProbe.current(this) == wifi &&
                    systemState(wifi.network) == InternetVerifier.State.UNKNOWN) runCheck(wifi)
            }, 650L)
        }
    }

    private fun schedulePortalRecovery(wifi: WifiProbe.Snapshot) {
        if (!RecoveryPrefs.enabled(this) || pendingRecovery != null ||
            lastPortalAttempt?.let { SystemClock.elapsedRealtime() - it < 90_000L } == true) return
        val task = Runnable {
            pendingRecovery = null
            if (monitor != null && runningCheck == null && WifiProbe.current(this) == wifi &&
                systemState(wifi.network) == InternetVerifier.State.PORTAL && RecoveryPrefs.enabled(this)) {
                lastPortalAttempt = SystemClock.elapsedRealtime()
                runCheck(wifi, repair = true, automatic = true)
            }
        }
        pendingRecovery = task
        ui.postDelayed(task, 2300L)
    }

    private fun cancelPortalRecovery() {
        pendingRecovery?.let { ui.removeCallbacks(it) }
        pendingRecovery = null
    }

    override fun onResume() {
        super.onResume()
        render()
        ReleaseUpdates.refresh(this)
    }

    override fun onStop() {
        cancelPortalRecovery()
        monitor?.let { connectivity.unregisterNetworkCallback(it) }
        monitor = null
        super.onStop()
    }

    override fun onWifiPermissionResult() {
        render()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }

}
