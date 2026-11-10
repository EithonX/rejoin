package org.campus.autoconnect

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : Activity() {
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

    private lateinit var mark: ConnectionMark
    private lateinit var network: TextView
    private lateinit var title: TextView
    private lateinit var detail: TextView
    private lateinit var progress: ProgressBar
    private lateinit var autoRow: LinearLayout
    private lateinit var historyRow: LinearLayout
    private lateinit var footer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = RejoinUi.column(this).apply { setBackgroundColor(RejoinUi.canvas) }
        val bar = RejoinUi.row(this).apply {
            setPaddingRelative(RejoinUi.dp(context, 24), RejoinUi.dp(context, 4),
                RejoinUi.dp(context, 16), RejoinUi.dp(context, 4))
        }
        bar.addView(RejoinUi.icon(this, R.drawable.ic_rejoin_mark, 32))
        bar.addView(RejoinUi.label(this, "Rejoin", 22f, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = RejoinUi.dp(this@MainActivity, 10) })
        bar.addView(RejoinUi.iconAction(this, R.drawable.ic_settings_outline, "Settings") {
            open(SettingsActivity::class.java)
        })
        RejoinUi.add(page, bar)

        val body = RejoinUi.scrollBody(page)
        val hero = RejoinUi.column(this, 20).apply {
            background = RejoinUi.surface(context, RejoinUi.panel, 24, RejoinUi.line)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val top = RejoinUi.row(this).apply { gravity = Gravity.TOP }
        val identity = RejoinUi.column(this)
        top.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        RejoinUi.add(identity, RejoinUi.label(this, "Wi-Fi connection", 13f, RejoinUi.muted, true))
        network = RejoinUi.label(this, "", 16f, bold = true)
        RejoinUi.add(identity, network, 3)
        mark = ConnectionMark(this)
        top.addView(mark, LinearLayout.LayoutParams(RejoinUi.dp(this, 82), RejoinUi.dp(this, 74))
            .apply { marginStart = RejoinUi.dp(this@MainActivity, 12) })
        RejoinUi.add(hero, top)
        title = RejoinUi.label(this, "", 36f, bold = true).apply { RejoinUi.heading(this) }
        RejoinUi.add(hero, title, 8)
        detail = RejoinUi.label(this, "", 15f, RejoinUi.muted)
        RejoinUi.add(hero, detail, 5)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(RejoinUi.accent)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        hero.addView(progress, LinearLayout.LayoutParams(-1, RejoinUi.dp(this, 4)).apply {
            topMargin = RejoinUi.dp(this@MainActivity, 16)
        })
        RejoinUi.add(body, hero, 4)

        RejoinUi.section(body, "Connection tools", 24)
        autoRow = RejoinUi.column(this)
        RejoinUi.add(body, autoRow, 4)
        RejoinUi.add(body, RejoinUi.rule(this))
        historyRow = RejoinUi.column(this)
        RejoinUi.add(body, historyRow)
        footer = RejoinUi.footer(page)

        setContentView(page)
        RejoinUi.applySystemBars(this, page)
        render()

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
        val permitted = RejoinUi.hasWifiAccess(this)
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

        network.text = profile?.label ?: wifi?.ssid ?: "Current network unavailable"
        autoRow.removeAllViews()
        autoRow.addView(RejoinUi.navRow(this, "Auto-reconnect", when {
            !hasProfiles || !consent || !hasAnyAccount -> "Setup needed"
            health.level == BackgroundHealth.Level.READY -> "On"
            health.level == BackgroundHealth.Level.PAUSED -> "Off"
            else -> "On · review background access"
        }, R.drawable.ic_shield) {
            if (profile != null && !hasAccount) openLoginFor(profile)
            else open(ProtectionActivity::class.java)
        })
        historyRow.removeAllViews()
        historyRow.addView(RejoinUi.navRow(this, "Activity",
            if (checkedHere && checkedAt > 0) "Last checked " +
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(checkedAt))
            else "Recent connection events", R.drawable.ic_activity) {
            open(ActivityLogActivity::class.java)
        })

        when {
            !hasProfiles -> show(ConnectionMark.Kind.IDLE, "Set up Rejoin",
                "Save your campus Wi-Fi and the login you already use.", "Set up Wi-Fi") {
                open(SetupActivity::class.java)
            }
            !permitted -> show(ConnectionMark.Kind.ATTENTION, "Wi-Fi access needed",
                "Allow precise Location so Android can share your Wi-Fi details.", "Allow Wi-Fi access") {
                RejoinUi.requestWifiAccess(this, WIFI_PERMISSION)
            }
            !locationOn -> show(ConnectionMark.Kind.ATTENTION, "Location is off",
                "Turn on Location so Rejoin can recognize your saved Wi-Fi.", "Open Location settings") {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            wifi == null -> show(ConnectionMark.Kind.IDLE, "Waiting for saved Wi-Fi",
                "Check that you’re connected to your campus Wi-Fi.", "Open Wi-Fi settings") {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            profile == null -> show(ConnectionMark.Kind.IDLE, "Wi-Fi isn’t saved",
                "Rejoin uses your campus login only on Wi-Fi you save.", "Review this Wi-Fi") {
                open(NetworksActivity::class.java)
            }
            runningCheck == wifi -> show(ConnectionMark.Kind.WORKING,
                if (signingIn) "Signing in…" else "Checking Wi-Fi…",
                if (signingIn) "Rejoin is checking and restoring your campus connection."
                else "Rejoin is checking internet access on this Wi-Fi.")
            state == InternetVerifier.State.ONLINE -> show(ConnectionMark.Kind.ONLINE, "You’re online",
                "Internet access is available on this Wi-Fi.", "Check again", filled = false) {
                runCheck(wifi)
            }
            !hasAccount -> show(ConnectionMark.Kind.ATTENTION, "Saved login needed",
                "Choose a campus login for this Wi-Fi.", "Set up login") {
                openLoginFor(profile)
            }
            !consent -> show(ConnectionMark.Kind.ATTENTION, "Review campus sign-in",
                "Rejoin needs your permission to use the saved login.", "Review sign-in") {
                open(SetupActivity::class.java)
            }
            currentReading?.failedSignIn == true -> show(ConnectionMark.Kind.ERROR, "Sign-in needs help",
                "Rejoin couldn’t confirm internet access. Try signing in from the campus page.",
                "Open campus sign-in") { RejoinUi.openCampusSignIn(this) }
            state == InternetVerifier.State.PORTAL -> show(ConnectionMark.Kind.ATTENTION,
                "Sign-in required", "Your campus Wi-Fi is asking you to sign in.", "Reconnect") {
                runCheck(wifi, repair = true)
            }
            else -> show(ConnectionMark.Kind.IDLE, "Check your connection",
                "Rejoin hasn’t confirmed internet access on this Wi-Fi.", "Check connection") {
                runCheck(wifi)
            }
        }
    }

    private fun show(kind: ConnectionMark.Kind, headline: String, description: String,
                     action: String? = null, filled: Boolean = true, onTap: () -> Unit = {}) {
        mark.kind = kind
        title.text = headline
        detail.text = description
        progress.visibility = if (kind == ConnectionMark.Kind.WORKING) View.VISIBLE else View.GONE
        footer.removeAllViews()
        footer.visibility = if (action == null) View.GONE else View.VISIBLE
        if (action != null) RejoinUi.add(footer, RejoinUi.action(this, action, filled, onTap))
    }

    private fun open(activity: Class<out Activity>) = startActivity(Intent(this, activity))

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
        val request = NetworkRequest.Builder().clearCapabilities()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
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
    }

    override fun onStop() {
        cancelPortalRecovery()
        monitor?.let { connectivity.unregisterNetworkCallback(it) }
        monitor = null
        super.onStop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WIFI_PERMISSION) {
            render()
            if (!RejoinUi.hasWifiAccess(this)) RejoinUi.explainWifiAccess(this)
        }
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val WIFI_PERMISSION = 41
    }
}
