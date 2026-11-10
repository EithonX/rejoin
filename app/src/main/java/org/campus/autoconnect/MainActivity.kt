package org.campus.autoconnect

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A single-task home screen: connection, appropriate next action, protection.
 * The screen does not advertise product capabilities or expose scheduler internals.
 */
class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var monitor: ConnectivityManager.NetworkCallback? = null
    private var pendingRepair: Runnable? = null
    private var foreground = false
    private var changeSwitch = false
    private var lastPortalEvent = 0L
    private var previousNetworkState: String? = null
    private var live: InternetVerifier.State? = null

    private lateinit var title: TextView
    private lateinit var description: TextView
    private lateinit var stateTag: TextView
    private lateinit var wifiName: TextView
    private lateinit var savedLogin: TextView
    private lateinit var primary: TextView
    private lateinit var secondary: TextView
    private lateinit var autoSwitch: Switch
    private lateinit var protectionTitle: TextView
    private lateinit var protectionDetail: TextView
    private lateinit var protectionHelp: TextView
    private lateinit var lastEvent: TextView
    private lateinit var lastCheck: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val outer = RejoinUi.column(this).apply { setBackgroundColor(RejoinUi.canvas) }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(RejoinUi.canvas)
            clipToPadding = false
            isFillViewport = true
        }
        val body = RejoinUi.column(this).apply {
            val side = RejoinUi.dp(this@MainActivity, 24)
            setPadding(side, RejoinUi.dp(this@MainActivity, 26),
                side, RejoinUi.dp(this@MainActivity, 32))
        }
        scroll.addView(body)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        setContentView(outer)
        RejoinUi.applySystemBars(this, outer)

        val brand = RejoinUi.row(this)
        brand.addView(RejoinUi.icon(this, R.drawable.ic_rejoin_mark, 37),
            LinearLayout.LayoutParams(RejoinUi.dp(this, 37), RejoinUi.dp(this, 37)))
        brand.addView(RejoinUi.label(this, "rejoin", 25f, RejoinUi.ink, true),
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                leftMargin = RejoinUi.dp(this@MainActivity, 10)
            })
        brand.addView(RejoinUi.smallAction(this, "Settings  ↗") { settings() })
        RejoinUi.add(body, brand)
        RejoinUi.add(body, RejoinUi.label(this,
            "YOUR CONNECTION", 11f, RejoinUi.muted, true).apply {
            letterSpacing = .13f
        }, 36)

        val status = RejoinUi.inset(this, RejoinUi.jadeDeep)
        status.background = RejoinUi.surface(this, RejoinUi.jadeDeep, 25)
        status.setPadding(RejoinUi.dp(this, 23), RejoinUi.dp(this, 25),
            RejoinUi.dp(this, 23), RejoinUi.dp(this, 23))
        RejoinUi.add(body, status, 12)

        val statusHead = RejoinUi.row(this)
        statusHead.addView(RejoinUi.label(this, "●", 15f, RejoinUi.mint),
            LinearLayout.LayoutParams(0, -2, 1f))
        stateTag = RejoinUi.label(this, "READY", 10f, RejoinUi.jadeDeep, true).apply {
            setPadding(RejoinUi.dp(this@MainActivity, 11), RejoinUi.dp(this@MainActivity, 7),
                RejoinUi.dp(this@MainActivity, 11), RejoinUi.dp(this@MainActivity, 7))
            background = RejoinUi.surface(this@MainActivity, RejoinUi.mint, 20)
        }
        statusHead.addView(stateTag)
        RejoinUi.add(status, statusHead)
        title = RejoinUi.label(this, "Ready when you are.", 28f, RejoinUi.white, true)
        RejoinUi.add(status, title, 23)
        description = RejoinUi.label(this,
            "We'll check the Wi-Fi when you need us.", 14f, RejoinUi.mint)
        description.setLineSpacing(RejoinUi.dp(this, 3).toFloat(), 1f)
        RejoinUi.add(status, description, 9)

        val heroSeparator = View(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(63, 115, 96))
        }
        status.addView(heroSeparator, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, RejoinUi.dp(this, 1)
        ).apply { topMargin = RejoinUi.dp(this@MainActivity, 23) })
        val row = RejoinUi.row(this)
        row.addView(RejoinUi.icon(this, R.drawable.ic_wifi, 21, RejoinUi.mint))
        val networkInfo = RejoinUi.column(this)
        row.addView(networkInfo, LinearLayout.LayoutParams(0, -2, 1f).apply {
            leftMargin = RejoinUi.dp(this@MainActivity, 12)
        })
        wifiName = RejoinUi.label(this, "No Wi-Fi saved", 14f, RejoinUi.white, true)
        RejoinUi.add(networkInfo, wifiName)
        savedLogin = RejoinUi.label(this, "Your login isn't saved yet", 12f, RejoinUi.mint)
        RejoinUi.add(networkInfo, savedLogin, 5)
        RejoinUi.add(status, row, 16)

        primary = RejoinUi.action(this, "Set up Rejoin", true) { settings() }
        RejoinUi.add(body, primary, 17)
        secondary = RejoinUi.smallAction(this, "Check again") { check(false) }.apply {
            gravity = Gravity.CENTER
        }
        RejoinUi.add(body, secondary, 6)

        val protection = RejoinUi.inset(this)
        RejoinUi.add(body, protection, 29)
        val protectRow = RejoinUi.row(this)
        val texts = RejoinUi.column(this)
        protectRow.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        protectionTitle = RejoinUi.label(this, "Automatic recovery", 17f, RejoinUi.ink, true)
        RejoinUi.add(texts, protectionTitle)
        protectionDetail = RejoinUi.label(this, "Ready to set up", 13f, RejoinUi.muted)
        protectionDetail.setLineSpacing(RejoinUi.dp(this, 2).toFloat(), 1f)
        RejoinUi.add(texts, protectionDetail, 7)
        autoSwitch = Switch(this).apply {
            thumbTintList = ColorStateList.valueOf(RejoinUi.jade)
            trackTintList = ColorStateList.valueOf(RejoinUi.mint)
            contentDescription = "Automatic recovery"
        }
        protectRow.addView(autoSwitch)
        RejoinUi.add(protection, protectRow)
        protectionHelp = RejoinUi.smallAction(this, "Review background access  ↗") {
            settings()
        }
        RejoinUi.add(protection, protectionHelp, 10)
        autoSwitch.setOnCheckedChangeListener { _, checked ->
            if (!changeSwitch) toggleRecovery(checked)
        }

        val details = RejoinUi.row(this)
        val column = RejoinUi.column(this)
        details.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
        RejoinUi.add(column, RejoinUi.label(this, "LAST CHECK", 10f, RejoinUi.muted, true)
            .apply { letterSpacing = .13f })
        lastCheck = RejoinUi.label(this, "Not yet", 14f, RejoinUi.ink, true)
        RejoinUi.add(column, lastCheck, 7)
        details.addView(RejoinUi.smallAction(this, "Activity  →") {
            startActivity(Intent(this, ActivityLogActivity::class.java))
        })
        RejoinUi.add(body, details, 28)
        lastEvent = RejoinUi.label(this, "", 13f, RejoinUi.muted)
        RejoinUi.add(body, lastEvent, 12)

        render()
    }

    private fun settings() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun toggleRecovery(enabled: Boolean) {
        val prefs = getSharedPreferences("profile", MODE_PRIVATE)
        if (enabled) {
            val saved = TrustedNetworks.all(this)
            val available = saved.any { CredentialVault.hasLoginFor(this, it) }
            if (!available || !prefs.getBoolean("http_opt_in", false)) {
                changeSwitch = true
                autoSwitch.isChecked = false
                changeSwitch = false
                settings()
                return
            }
            prefs.edit().putBoolean("auto_enabled", true).commit()
            val scheduled = RecoveryScheduler.schedule(this, 20_000L)
            RecoveryScheduler.registerWifiEvents(this)
            if (!scheduled) {
                prefs.edit().putBoolean("auto_enabled", false).commit()
                changeSwitch = true
                autoSwitch.isChecked = false
                changeSwitch = false
            } else ActivityHistory.record(this, "Automatic recovery enabled")
        } else {
            prefs.edit().putBoolean("auto_enabled", false).commit()
            RecoveryScheduler.disable(this)
            ActivityHistory.record(this, "Automatic recovery paused")
        }
        render()
    }

    private fun check(repair: Boolean) {
        if (!busy.compareAndSet(false, true)) return
        title.text = if (repair) "Reconnecting…" else "Checking connection…"
        description.text = "Using your trusted Wi-Fi."
        primary.isEnabled = false
        worker.execute {
            try {
                val outcome: String
                val result: InternetVerifier.State
                if (repair) {
                    outcome = SessionEngine.run(applicationContext, userInitiated = true,
                        progress = { step -> main.post {
                            if (busy.get()) description.text = step
                        } })
                    result = when {
                        outcome.contains("LOGIN VERIFIED", true) ||
                            outcome.contains("Internet online", true) -> InternetVerifier.State.ONLINE
                        outcome.contains("portal", true) -> InternetVerifier.State.PORTAL
                        else -> InternetVerifier.State.UNKNOWN
                    }
                } else {
                    val check = InternetVerifier.check(applicationContext)
                    result = check.state
                    outcome = check.details
                    val profile = WifiProbe.current(applicationContext)
                        ?.let { TrustedNetworks.find(applicationContext, it) }
                    getSharedPreferences("profile", MODE_PRIVATE).edit()
                        .putLong("last_checked", System.currentTimeMillis())
                        .putString("last_profile_id", profile?.id)
                        .putString("last_result", outcome.take(200))
                        .putString("last_class", when (result) {
                            InternetVerifier.State.ONLINE -> "healthy"
                            InternetVerifier.State.PORTAL -> "portal"
                            InternetVerifier.State.UNKNOWN -> "unknown"
                        }).apply()
                    if (result == InternetVerifier.State.ONLINE)
                        NetworkStatusHint.requestAfterVerifiedOnline(applicationContext)
                }
                main.post {
                    live = result
                    busy.set(false)
                    render()
                    if (repair && result == InternetVerifier.State.UNKNOWN)
                        description.text = outcome.take(125)
                }
            } catch (e: Exception) {
                main.post {
                    busy.set(false)
                    live = InternetVerifier.State.UNKNOWN
                    render()
                    description.text = "Couldn't finish checking the connection."
                }
            }
        }
    }

    private fun render() {
        if (!::title.isInitialized) return
        val prefs = getSharedPreferences("profile", MODE_PRIVATE)
        val profiles = TrustedNetworks.all(this)
        val active = WifiProbe.current(this)?.let { TrustedNetworks.find(this, it) }
        val account = active?.let { CredentialVault.hasLoginFor(this, it) } ?: false
        wifiName.text = active?.label ?: if (profiles.isNotEmpty())
            "Away from saved Wi-Fi" else "No Wi-Fi saved"
        savedLogin.text = when {
            active != null && account -> "✓  Login saved on this device"
            active != null -> "No login saved for this Wi-Fi"
            profiles.isEmpty() -> "Save your Wi-Fi in Settings"
            else -> "Watching for your saved networks"
        }
        changeSwitch = true
        autoSwitch.isChecked = RecoveryPrefs.enabled(this)
        changeSwitch = false
        val health = BackgroundHealth.inspect(this)
        protectionTitle.text = when (health.level) {
            BackgroundHealth.Level.READY -> "Automatic recovery"
            BackgroundHealth.Level.PAUSED -> "Automatic recovery"
            BackgroundHealth.Level.NEEDS_ACTION -> "Background access needs attention"
        }
        protectionDetail.text = when (health.level) {
            BackgroundHealth.Level.READY -> "On · Android is managing background checks"
            BackgroundHealth.Level.PAUSED -> "Off"
            BackgroundHealth.Level.NEEDS_ACTION -> health.description
        }
        protectionHelp.visibility = if (health.level == BackgroundHealth.Level.NEEDS_ACTION)
            View.VISIBLE else View.GONE

        val checked = prefs.getLong("last_checked", 0L)
        lastCheck.text = if (checked > 0L)
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(checked)) else "Not yet"
        val items = ActivityHistory.latest(this, 1)
        lastEvent.text = items.firstOrNull()?.event ?: "Nothing needs your attention."

        val recent = checked > 0 &&
            System.currentTimeMillis() - checked in 0L..1_200_000L &&
            prefs.getString("last_profile_id", null) == active?.id
        val connection = live ?: when {
            recent && prefs.getString("last_class", null) in listOf("healthy", "restored") ->
                InternetVerifier.State.ONLINE
            recent && prefs.getString("last_class", null) == "portal" ->
                InternetVerifier.State.PORTAL
            else -> InternetVerifier.State.UNKNOWN
        }

        var primaryLabel = ""
        var primaryAction: () -> Unit = {}
        var secondaryLabel = ""
        var secondaryAction: () -> Unit = {}
        if (!busy.get()) {
            when {
                profiles.isEmpty() -> {
                    title.text = "Let's get you set up."
                    description.text = "Save a Wi-Fi network and your campus login."
                    stateTag.text = "SETUP"
                    primaryLabel = "Set up Rejoin"
                    primaryAction = { settings() }
                }
                active == null -> {
                    title.text = "Standing by."
                    description.text = "Rejoin is ready when you connect to a saved Wi-Fi."
                    stateTag.text = "AWAY"
                    primaryLabel = "Manage saved Wi-Fi"
                    primaryAction = { settings() }
                }
                !account -> {
                    title.text = "One last step."
                    description.text = "Save your campus login to protect this Wi-Fi."
                    stateTag.text = "SETUP"
                    primaryLabel = "Save your login"
                    primaryAction = {
                        startActivity(Intent(this, LoginsActivity::class.java))
                    }
                }
                connection == InternetVerifier.State.ONLINE -> {
                    title.text = "You're connected."
                    description.text = "Internet is available on " + active.label + "."
                    stateTag.text = "ONLINE"
                    secondaryLabel = "Check again"
                    secondaryAction = { check(false) }
                }
                connection == InternetVerifier.State.PORTAL -> {
                    title.text = "Sign-in needed."
                    description.text = "Your campus session needs a fresh sign-in."
                    stateTag.text = "NEEDS LOGIN"
                    primaryLabel = "Reconnect"
                    primaryAction = { check(true) }
                    secondaryLabel = "Check again"
                    secondaryAction = { check(false) }
                }
                else -> {
                    title.text = "Ready to check."
                    description.text = "Your connection hasn't been verified recently."
                    stateTag.text = "READY"
                    primaryLabel = "Check connection"
                    primaryAction = { check(false) }
                    secondaryLabel = "Try reconnecting"
                    secondaryAction = { check(true) }
                }
            }
        }
        primary.visibility = if (primaryLabel.isBlank()) View.GONE else View.VISIBLE
        primary.text = primaryLabel
        primary.isEnabled = !busy.get()
        primary.setOnClickListener { primaryAction() }
        secondary.visibility = if (secondaryLabel.isBlank()) View.GONE else View.VISIBLE
        secondary.text = secondaryLabel
        secondary.setOnClickListener { secondaryAction() }
    }

    override fun onStart() {
        super.onStart()
        foreground = true
        live = null
        if (RecoveryPrefs.enabled(this)) {
            // Upgrade older one-shot installations in place without user action.
            RecoveryScheduler.schedulePeriodic(this)
            RecoveryScheduler.registerWifiEvents(this)
        }
        render()
        val request = NetworkRequest.Builder().clearCapabilities()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val cb = if (Build.VERSION.SDK_INT >= 31)
            object : ConnectivityManager.NetworkCallback(
                ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    handleWifiEvent(network, caps)
                }
            } else object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    handleWifiEvent(network, caps)
                }
            }
        try {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .registerNetworkCallback(request, cb)
            monitor = cb
        } catch (_: Exception) {}
        val last = getSharedPreferences("profile", MODE_PRIVATE)
            .getLong("last_checked", 0L)
        if (LiveSessionTest.selectedWifiMatches(this) &&
            System.currentTimeMillis() - last > 120_000L)
            main.postDelayed({ if (foreground) check(false) }, 600L)
    }

    private fun handleWifiEvent(network: Network, caps: NetworkCapabilities) {
        val state = when {
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "online"
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) -> "portal"
            else -> "unknown"
        }
        main.post {
            if (!foreground) return@post
            val snapshot = WifiProbe.current(this, network) ?: return@post
            if (TrustedNetworks.find(this, snapshot) == null) return@post
            val signature = network.toString() + "|" + state
            if (signature == previousNetworkState) return@post
            previousNetworkState = signature
            if (state == "online") {
                live = InternetVerifier.State.ONLINE
                render()
            } else if (state == "portal" && RecoveryPrefs.enabled(this) &&
                System.currentTimeMillis() - lastPortalEvent > 90_000L) {
                lastPortalEvent = System.currentTimeMillis()
                pendingRepair?.let { main.removeCallbacks(it) }
                val action = Runnable { if (foreground) check(true) }
                pendingRepair = action
                main.postDelayed(action, 2_200L)
            }
        }
    }

    override fun onStop() {
        foreground = false
        pendingRepair?.let { main.removeCallbacks(it) }
        pendingRepair = null
        monitor?.let {
            try {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        monitor = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::title.isInitialized) render()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
