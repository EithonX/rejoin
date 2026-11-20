package com.eithonx.rejoin

import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : RejoinActivity() {
    private val ui = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val prefs by lazy { getSharedPreferences("profile", MODE_PRIVATE) }
    private var monitor: ConnectivityManager.NetworkCallback? = null
    private var pendingRecovery: Runnable? = null
    private var foreground = false
    private var observedNetwork: Network? = null
    private var androidState: InternetVerifier.State? = null
    private var androidChangedAt = 0L
    private var lastForegroundWifi: WifiProbe.Snapshot? = null
    private var recheckAfterWifiSettings = false
    private val preferencesChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (foreground && (key == "last_checked" || key == "last_outcome" ||
                key == "auto_enabled" || key?.startsWith("login_rejection_") == true)) {
            render()
        }
    }
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
        recheckAfterWifiSettings = savedInstanceState?.getBoolean(RECHECK_AFTER_WIFI_SETTINGS) == true
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
        val account = profile?.let { CredentialVault.resolve(this, it) }
        val hasAccount = account != null
        val rejected = account != null && CredentialVault.wasRejected(this, account)
        val consent = prefs.getBoolean("http_opt_in", false)
        val hasAnyAccount = hasAccount || (profile == null && CredentialVault.hasCredentials(this))
        val health = BackgroundHealth.inspect(this)
        val reading = if (wifi != null && profile != null) currentObservation(profile.id, wifi) else null
        val state = reading?.state ?: wifi?.let { systemState(it.network) } ?: InternetVerifier.State.UNKNOWN
        val checkedAt = reading?.at ?: 0L
        val outcome = reading?.outcome

        needsLoginFor = profile?.takeIf { !hasAccount }
        val automation = when {
            !hasProfiles || !consent || !hasAnyAccount -> "Setup needed"
            !health.enabled -> "Off"
            rejected -> "Login rejected"
            health.issue?.required == true -> "Needs attention"
            else -> "On"
        }
        val lastChecked = if (checkedAt > 0)
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
            state == InternetVerifier.State.SECURE_PORTAL -> HomeStatus("Finish sign-in in Android",
                "Open your Wi-Fi network to sign in. Rejoin will check the connection when you return.",
                ConnectionTone.ATTENTION, "Open Wi-Fi settings", ::openWifiSettings)
            state == InternetVerifier.State.CERTIFICATE_ERROR -> HomeStatus("Certificate verification failed",
                "If Android also shows a certificate warning, check with campus IT before entering your password.",
                ConnectionTone.ERROR, "Open Wi-Fi settings", ::openWifiSettings)
            state == InternetVerifier.State.UNSUPPORTED_PORTAL -> HomeStatus("Wi-Fi sign-in address changed",
                "Use Android’s Wi-Fi sign-in for this connection.", ConnectionTone.ATTENTION,
                "Open Wi-Fi settings", ::openWifiSettings)
            !hasAccount -> HomeStatus("Login needed",
                "Choose the login you use on this Wi-Fi.", ConnectionTone.ATTENTION, "Set up login") {
                openLoginFor(profile)
            }
            !consent -> HomeStatus("Sign-in permission needed",
                "Review how Rejoin uses your saved login.", ConnectionTone.ATTENTION, "Review sign-in") {
                open(SetupActivity::class.java)
            }
            rejected && account != null -> HomeStatus("Wi-Fi login rejected",
                "Update the saved username or password. Auto sign-in is paused for this login.",
                ConnectionTone.ERROR, "Update saved login") { editLogin(account.id) }
            outcome == RecoveryPolicy.Outcome.COOLDOWN && health.enabled -> HomeStatus("Waiting to retry",
                "Rejoin will try again on its next eligible check.", ConnectionTone.ATTENTION,
                "Sign in now") { runCheck(wifi, repair = true) }
            outcome == RecoveryPolicy.Outcome.PORTAL_FORM_FAILED -> HomeStatus("Sign-in page unavailable",
                "Rejoin couldn’t load the Wi-Fi sign-in form.", ConnectionTone.ERROR,
                "Open sign-in page", ::openSignInPage)
            outcome == RecoveryPolicy.Outcome.VERIFY_FAILED -> HomeStatus("Sign-in couldn’t be confirmed",
                "Check the connection again or open the Wi-Fi sign-in page.", ConnectionTone.ATTENTION,
                "Check connection") { runCheck(wifi) }
            outcome == RecoveryPolicy.Outcome.FAILED -> HomeStatus("Connection check failed",
                actionLabel = "Check connection", tone = ConnectionTone.ATTENTION) { runCheck(wifi) }
            state == InternetVerifier.State.PORTAL -> HomeStatus("Sign-in required",
                "This Wi-Fi is asking you to sign in.", ConnectionTone.ATTENTION, "Sign in") {
                runCheck(wifi, repair = true)
            }
            else -> HomeStatus("Connection unverified",
                actionLabel = "Check connection") { runCheck(wifi, automatic = health.enabled) }
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

    private fun editLogin(id: String) {
        startActivity(Intent(this, LoginEditorActivity::class.java).putExtra(LoginEditorActivity.EXTRA_ID, id))
    }

    private fun openWifiSettings() {
        recheckAfterWifiSettings = true
        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    private fun currentObservation(profileId: String, wifi: WifiProbe.Snapshot): ConnectionObservation? =
        ConnectionObservation.read(prefs, profileId, wifi)?.takeIf {
            observedNetwork != wifi.network || it.at >= androidChangedAt
        }

    private fun runCheck(wifi: WifiProbe.Snapshot, repair: Boolean = false, automatic: Boolean = false) {
        if (runningCheck != null || WifiProbe.current(this) != wifi) return
        val profile = TrustedNetworks.find(this, wifi) ?: return
        runningCheck = wifi
        signingIn = repair
        render()
        executor.execute {
            try {
                if (repair || automatic) {
                    SessionEngine.run(applicationContext, userInitiated = repair,
                        requiredNetwork = wifi.network, requiredProfileId = profile.id,
                        onSubmitting = {
                            if (automatic) ui.post {
                                if (runningCheck == wifi) {
                                    signingIn = true
                                    render()
                                }
                            }
                        })
                } else {
                    val check = InternetVerifier.check(applicationContext, wifi.network)
                    if (WifiProbe.current(applicationContext, wifi.network) == wifi) {
                        val editor = prefs.edit()
                        ConnectionObservation(check.state, ConnectionObservation.outcomeFor(check.state))
                            .write(editor, profile.id, wifi, check.details)
                        editor.apply()
                        if (check.state == InternetVerifier.State.ONLINE) {
                            NetworkStatusHint.requestAfterVerifiedOnline(applicationContext)
                        }
                    }
                }
            } catch (_: RuntimeException) {
                if (WifiProbe.current(applicationContext, wifi.network) == wifi) {
                    val editor = prefs.edit()
                    ConnectionObservation(InternetVerifier.State.UNKNOWN, RecoveryPolicy.Outcome.FAILED)
                        .write(editor, profile.id, wifi, "Connection check failed")
                    editor.apply()
                }
            } finally {
                ui.post {
                    runningCheck = null
                    if (!isDestroyed) render()
                }
            }
        }
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
        foreground = true
        lastForegroundWifi = null
        prefs.registerOnSharedPreferenceChangeListener(preferencesChanged)
        if (RecoveryPrefs.enabled(this)) {
            RecoveryScheduler.schedulePeriodic(this)
            RecoveryScheduler.registerWifiEvents(this)
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                ui.post {
                    if (monitor !== this) return@post
                    val wifi = WifiProbe.current(this@MainActivity)
                    if (wifi?.network == network) {
                        val state = systemState(network)
                        val becameOnline = state == InternetVerifier.State.ONLINE &&
                            (observedNetwork != network || androidState != state)
                        if (observedNetwork != network) {
                            observedNetwork = network
                            androidChangedAt = 0L
                        } else if (androidState != state) {
                            androidChangedAt = System.currentTimeMillis()
                            lastForegroundWifi = null
                        }
                        androidState = state
                        if (becameOnline) {
                            ReleaseUpdates.refresh(this@MainActivity, connectionRestored = true)
                        } else if (state != InternetVerifier.State.ONLINE) {
                            scheduleForegroundCheck()
                        }
                    }
                    render()
                }
            }

            override fun onLost(network: Network) {
                ui.post {
                    if (monitor !== this) return@post
                    if (observedNetwork == network) {
                        observedNetwork = null
                        androidState = null
                        lastForegroundWifi = null
                    }
                    render()
                    scheduleForegroundCheck()
                }
            }
        }
        try {
            connectivity.registerNetworkCallback(RecoveryScheduler.wifiRequest(), callback)
            monitor = callback
        } catch (_: RuntimeException) {
            // The initial check still runs if Android refuses the callback.
        }
    }

    private fun scheduleForegroundCheck() {
        if (!foreground || pendingRecovery != null || runningCheck != null) return
        val wifi = WifiProbe.current(this) ?: return
        if (lastForegroundWifi == wifi) return
        val profile = TrustedNetworks.find(this, wifi) ?: return
        val observed = currentObservation(profile.id, wifi)
        val state = observed?.state ?: systemState(wifi.network)
        if (state == InternetVerifier.State.ONLINE) return
        val auto = RecoveryPrefs.enabled(this)
        if (!auto && (observed != null || state != InternetVerifier.State.UNKNOWN)) return
        if (observed?.outcome?.stopsAutomaticSubmission == true) {
            val login = CredentialVault.resolve(this, profile)
            if (observed.outcome != RecoveryPolicy.Outcome.REJECTED ||
                (login != null && CredentialVault.wasRejected(this, login))) return
        }
        val task = Runnable {
            pendingRecovery = null
            if (foreground && runningCheck == null) {
                if (WifiProbe.current(this) != wifi) {
                    scheduleForegroundCheck()
                } else if (systemState(wifi.network) != InternetVerifier.State.ONLINE) {
                    lastForegroundWifi = wifi
                    runCheck(wifi, automatic = RecoveryPrefs.enabled(this))
                }
            }
        }
        pendingRecovery = task
        ui.postDelayed(task, 650L)
    }

    override fun onResume() {
        super.onResume()
        render()
        if (recheckAfterWifiSettings) {
            recheckAfterWifiSettings = false
            WifiProbe.current(this)?.let {
                lastForegroundWifi = it
                runCheck(it)
            }
        }
        scheduleForegroundCheck()
        ReleaseUpdates.refresh(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(RECHECK_AFTER_WIFI_SETTINGS, recheckAfterWifiSettings)
    }

    override fun onStop() {
        foreground = false
        prefs.unregisterOnSharedPreferenceChangeListener(preferencesChanged)
        pendingRecovery?.let { ui.removeCallbacks(it) }
        pendingRecovery = null
        monitor?.let { connectivity.unregisterNetworkCallback(it) }
        monitor = null
        observedNetwork = null
        androidState = null
        androidChangedAt = 0L
        super.onStop()
    }

    override fun onWifiPermissionResult() {
        render()
        scheduleForegroundCheck()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val RECHECK_AFTER_WIFI_SETTINGS = "recheck_after_wifi_settings"
    }
}
