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
 * Rejoin is a utility, not a dashboard. A glance answers: Am I connected?
 * Will the app handle the next expiry? If not, what can I do?
 */
class MainActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var foreground = false
    private var networkMonitor: ConnectivityManager.NetworkCallback? = null
    private var pendingRecovery: Runnable? = null
    private var lastPortalEvent = 0L
    private var lastNetworkKey: String? = null
    private var ignoreSwitch = false
    private var observed: InternetVerifier.State? = null

    private lateinit var stateMark: ConnectionMark
    private lateinit var eyebrow: TextView
    private lateinit var headline: TextView
    private lateinit var explanation: TextView
    private lateinit var networkLine: TextView
    private lateinit var primaryButton: TextView
    private lateinit var alternateButton: TextView
    private lateinit var autoSwitch: Switch
    private lateinit var autoText: TextView
    private lateinit var autoHint: TextView
    private lateinit var troubleshoot: TextView
    private lateinit var timestamp: TextView
    private lateinit var activityText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this).apply {
            setBackgroundColor(RejoinUi.canvas)
            clipToPadding = false
            isFillViewport = true
        }
        val body = RejoinUi.column(this).apply {
            setPadding(RejoinUi.dp(this@MainActivity, 25),
                RejoinUi.dp(this@MainActivity, 21),
                RejoinUi.dp(this@MainActivity, 25),
                RejoinUi.dp(this@MainActivity, 30))
        }
        root.addView(body)
        setContentView(root)
        RejoinUi.applySystemBars(this, root)

        val header = RejoinUi.row(this)
        header.addView(RejoinUi.icon(this, R.drawable.ic_rejoin_mark, 35),
            LinearLayout.LayoutParams(RejoinUi.dp(this, 35), RejoinUi.dp(this, 35)))
        header.addView(RejoinUi.label(this, "rejoin", 23f, RejoinUi.ink, true),
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                leftMargin = RejoinUi.dp(this@MainActivity, 10)
            })
        val settings = RejoinUi.smallAction(this, "Settings") { openSettings() }
        settings.contentDescription = "Open Rejoin settings"
        header.addView(settings)
        RejoinUi.add(body, header)

        // One focal area; no feature cards, fake metrics, or repeated labels.
        val focus = RejoinUi.column(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, RejoinUi.dp(this@MainActivity, 50), 0,
                RejoinUi.dp(this@MainActivity, 8))
        }
        RejoinUi.add(body, focus, 18)
        stateMark = ConnectionMark(this).apply {
            kind = ConnectionMark.Kind.IDLE
        }
        focus.addView(stateMark, LinearLayout.LayoutParams(
            RejoinUi.dp(this, 88), RejoinUi.dp(this, 88)
        ))

        eyebrow = RejoinUi.label(this, "CONNECTION", 11f, RejoinUi.muted, true).apply {
            letterSpacing = .14f
            gravity = Gravity.CENTER
        }
        RejoinUi.add(focus, eyebrow, 27)
        headline = RejoinUi.label(this, "Ready to connect.", 30f, RejoinUi.ink, true).apply {
            gravity = Gravity.CENTER
            maxLines = 2
        }
        RejoinUi.add(focus, headline, 12)
        explanation = RejoinUi.label(this, "", 14f, RejoinUi.muted).apply {
            gravity = Gravity.CENTER
            maxLines = 3
            setLineSpacing(RejoinUi.dp(this@MainActivity, 4).toFloat(), 1f)
        }
        RejoinUi.add(focus, explanation, 11)
        networkLine = RejoinUi.label(this, "", 12f, RejoinUi.muted, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        RejoinUi.add(focus, networkLine, 18)

        primaryButton = RejoinUi.action(this, "Check connection") { runCheck(false) }
        RejoinUi.add(body, primaryButton, 17)
        alternateButton = RejoinUi.smallAction(this, "Check again") { runCheck(false) }
            .apply { gravity = Gravity.CENTER }
        RejoinUi.add(body, alternateButton, 6)

        val separation = View(this).apply { setBackgroundColor(RejoinUi.line) }
        body.addView(separation, LinearLayout.LayoutParams(-1, RejoinUi.dp(this, 1)).apply {
            topMargin = RejoinUi.dp(this@MainActivity, 38)
        })

        val protectionRow = RejoinUi.row(this).apply {
            setPadding(0, RejoinUi.dp(this@MainActivity, 18), 0,
                RejoinUi.dp(this@MainActivity, 16))
        }
        val protectionCopy = RejoinUi.column(this)
        protectionRow.addView(protectionCopy, LinearLayout.LayoutParams(0, -2, 1f))
        RejoinUi.add(protectionCopy,
            RejoinUi.label(this, "Auto-reconnect", 16f, RejoinUi.ink, true))
        autoText = RejoinUi.label(this, "Off", 13f, RejoinUi.muted)
        RejoinUi.add(protectionCopy, autoText, 6)
        autoSwitch = Switch(this).apply {
            contentDescription = "Automatic Wi-Fi reconnection"
            thumbTintList = ColorStateList.valueOf(RejoinUi.jade)
            trackTintList = ColorStateList.valueOf(RejoinUi.raised)
        }
        protectionRow.addView(autoSwitch)
        RejoinUi.add(body, protectionRow)
        autoSwitch.setOnCheckedChangeListener { _, enabled ->
            if (!ignoreSwitch) setAutoRecovery(enabled)
        }

        autoHint = RejoinUi.label(this, "", 12f, RejoinUi.amber).apply {
            maxLines = 3
        }
        RejoinUi.add(body, autoHint, 2)
        troubleshoot = RejoinUi.smallAction(this, "Fix background access  →") {
            openSettings()
        }
        RejoinUi.add(body, troubleshoot, 3)

        // Last meaningful facts are secondary, not the app's content.
        val lowerLine = View(this).apply { setBackgroundColor(RejoinUi.line) }
        body.addView(lowerLine, LinearLayout.LayoutParams(-1, RejoinUi.dp(this, 1)).apply {
            topMargin = RejoinUi.dp(this@MainActivity, 23)
        })
        val lastRow = RejoinUi.row(this).apply {
            setPadding(0, RejoinUi.dp(this@MainActivity, 16), 0, 0)
        }
        val lastInfo = RejoinUi.column(this)
        lastRow.addView(lastInfo, LinearLayout.LayoutParams(0, -2, 1f))
        RejoinUi.add(lastInfo, RejoinUi.label(this, "Last checked", 12f, RejoinUi.muted))
        timestamp = RejoinUi.label(this, "Not yet", 14f, RejoinUi.ink, true)
        RejoinUi.add(lastInfo, timestamp, 5)
        lastRow.addView(RejoinUi.smallAction(this, "Activity  →") {
            startActivity(Intent(this, ActivityLogActivity::class.java))
        })
        RejoinUi.add(body, lastRow, 0)
        activityText = RejoinUi.label(this, "", 12f, RejoinUi.muted)
        RejoinUi.add(body, activityText, 13)
        render()
        val prefs = getSharedPreferences("profile", MODE_PRIVATE)
        val setUp = TrustedNetworks.all(this).isNotEmpty() &&
            CredentialVault.hasCredentials(this) &&
            prefs.getBoolean("http_opt_in", false)
        if (setUp) prefs.edit().putBoolean("onboarding_completed", true).apply()
        if (savedInstanceState == null && SetupFlow.shouldAutoOpen(
                setUp,
                prefs.getBoolean("onboarding_completed", false),
                prefs.getBoolean("onboarding_deferred", false))) {
            startActivity(Intent(this, SetupActivity::class.java))
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun setAutoRecovery(enabled: Boolean) {
        val prefs = getSharedPreferences("profile", MODE_PRIVATE)
        if (enabled) {
            val ready = TrustedNetworks.all(this).any {
                CredentialVault.hasLoginFor(this, it)
            } && prefs.getBoolean("http_opt_in", false)
            if (!ready) {
                ignoreSwitch = true
                autoSwitch.isChecked = false
                ignoreSwitch = false
                openSettings()
                return
            }
            prefs.edit().putBoolean("auto_enabled", true).commit()
            if (RecoveryScheduler.schedule(this, 15_000L)) {
                RecoveryScheduler.registerWifiEvents(this)
                ActivityHistory.record(this, "Automatic recovery enabled")
            } else {
                prefs.edit().putBoolean("auto_enabled", false).commit()
                ignoreSwitch = true
                autoSwitch.isChecked = false
                ignoreSwitch = false
            }
        } else {
            prefs.edit().putBoolean("auto_enabled", false).commit()
            RecoveryScheduler.disable(this)
            ActivityHistory.record(this, "Automatic recovery paused")
        }
        render()
    }

    private fun runCheck(repair: Boolean) {
        if (!busy.compareAndSet(false, true)) return
        stateMark.kind = ConnectionMark.Kind.WORKING
        headline.text = if (repair) "Reconnecting…" else "Checking…"
        explanation.text = "Checking your saved Wi-Fi."
        primaryButton.isEnabled = false
        executor.execute {
            try {
                val result: InternetVerifier.State
                val message: String
                if (repair) {
                    message = SessionEngine.run(applicationContext,
                        userInitiated = true, progress = { })
                    result = when {
                        message.contains("LOGIN VERIFIED", true) ||
                            message.contains("Internet online", true) -> InternetVerifier.State.ONLINE
                        message.contains("portal", true) -> InternetVerifier.State.PORTAL
                        else -> InternetVerifier.State.UNKNOWN
                    }
                } else {
                    val check = InternetVerifier.check(applicationContext)
                    result = check.state
                    message = check.details
                    val profile = WifiProbe.current(applicationContext)?.let {
                        TrustedNetworks.find(applicationContext, it)
                    }
                    getSharedPreferences("profile", MODE_PRIVATE).edit()
                        .putLong("last_checked", System.currentTimeMillis())
                        .putString("last_profile_id", profile?.id)
                        .putString("last_result", message.take(200))
                        .putString("last_class", when (result) {
                            InternetVerifier.State.ONLINE -> "healthy"
                            InternetVerifier.State.PORTAL -> "portal"
                            InternetVerifier.State.UNKNOWN -> "unknown"
                        }).apply()
                    if (result == InternetVerifier.State.ONLINE) {
                        NetworkStatusHint.requestAfterVerifiedOnline(applicationContext)
                    }
                }
                ui.post {
                    busy.set(false)
                    observed = result
                    render()
                }
            } catch (_: Exception) {
                ui.post {
                    busy.set(false)
                    observed = InternetVerifier.State.UNKNOWN
                    render()
                }
            }
        }
    }

    private fun render() {
        if (!::headline.isInitialized) return
        val prefs = getSharedPreferences("profile", MODE_PRIVATE)
        val profiles = TrustedNetworks.all(this)
        val current = WifiProbe.current(this)?.let { TrustedNetworks.find(this, it) }
        val hasAccount = current?.let { CredentialVault.hasLoginFor(this, it) } ?: false
        val checkedAt = prefs.getLong("last_checked", 0L)
        val recent = current != null && checkedAt > 0 &&
            System.currentTimeMillis() - checkedAt in 0L..1_200_000L &&
            current.id == prefs.getString("last_profile_id", null)
        val cachedState = when {
            recent && prefs.getString("last_class", "") in listOf("healthy", "restored") ->
                InternetVerifier.State.ONLINE
            recent && prefs.getString("last_class", "") == "portal" ->
                InternetVerifier.State.PORTAL
            else -> InternetVerifier.State.UNKNOWN
        }
        val state = observed ?: cachedState
        networkLine.text = current?.label ?: if (profiles.isEmpty()) ""
            else "Saved networks: " + profiles.size

        val healthy = BackgroundHealth.inspect(this)
        ignoreSwitch = true
        autoSwitch.isChecked = RecoveryPrefs.enabled(this)
        ignoreSwitch = false
        autoText.text = when (healthy.level) {
            BackgroundHealth.Level.PAUSED -> "Off"
            BackgroundHealth.Level.READY -> "Active in the background"
            BackgroundHealth.Level.NEEDS_ACTION -> "Needs your attention"
        }
        autoHint.visibility = if (healthy.level == BackgroundHealth.Level.NEEDS_ACTION)
            View.VISIBLE else View.GONE
        autoHint.text = if (healthy.level == BackgroundHealth.Level.NEEDS_ACTION)
            healthy.description else ""
        troubleshoot.visibility = if (healthy.level == BackgroundHealth.Level.NEEDS_ACTION)
            View.VISIBLE else View.GONE

        timestamp.text = if (checkedAt > 0)
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(checkedAt)) else "Not yet"
        activityText.text = ActivityHistory.latest(this, 1).firstOrNull()?.event
            ?: "No connection events yet."

        var primary: String? = null
        var secondary: String? = null
        var action: () -> Unit = {}
        var extra: () -> Unit = {}
        if (!busy.get()) when {
            profiles.isEmpty() -> {
                stateMark.kind = ConnectionMark.Kind.IDLE
                eyebrow.text = "WELCOME"
                headline.text = "Wi-Fi, handled."
                explanation.text = "Save your campus Wi-Fi and login once."
                primary = "Get started"
                action = { startActivity(Intent(this, SetupActivity::class.java)) }
            }
            current == null -> {
                stateMark.kind = ConnectionMark.Kind.IDLE
                eyebrow.text = "STANDING BY"
                headline.text = "Away from campus."
                explanation.text = "Rejoin will be ready on your saved Wi-Fi."
            }
            !hasAccount -> {
                stateMark.kind = ConnectionMark.Kind.ATTENTION
                eyebrow.text = "ONE MORE STEP"
                headline.text = "Login not saved."
                explanation.text = "Add an account for this network."
                primary = "Save login"
                action = { startActivity(Intent(this, SetupActivity::class.java)) }
            }
            state == InternetVerifier.State.ONLINE -> {
                stateMark.kind = ConnectionMark.Kind.ONLINE
                eyebrow.text = "ONLINE"
                headline.text = "Connected."
                explanation.text = if (RecoveryPrefs.enabled(this))
                    "You're online. Rejoin is ready if the session expires."
                else "You're online. Auto-reconnect is off."
                secondary = "Check again"
                extra = { runCheck(false) }
            }
            state == InternetVerifier.State.PORTAL -> {
                stateMark.kind = ConnectionMark.Kind.ATTENTION
                eyebrow.text = "ACTION NEEDED"
                headline.text = "Sign-in required."
                explanation.text = "Your campus session has expired."
                primary = "Reconnect now"
                action = { runCheck(true) }
                secondary = "Check again"
                extra = { runCheck(false) }
            }
            else -> {
                stateMark.kind = ConnectionMark.Kind.IDLE
                eyebrow.text = "STATUS UNKNOWN"
                headline.text = "Let's check."
                explanation.text = "Rejoin hasn't confirmed internet access recently."
                primary = "Check connection"
                action = { runCheck(false) }
                secondary = "Try reconnecting"
                extra = { runCheck(true) }
            }
        }

        if (busy.get()) {
            stateMark.kind = ConnectionMark.Kind.WORKING
            eyebrow.text = "PLEASE WAIT"
            primaryButton.visibility = View.GONE
            alternateButton.visibility = View.GONE
        } else {
            primaryButton.visibility = if (primary == null) View.GONE else View.VISIBLE
            alternateButton.visibility = if (secondary == null) View.GONE else View.VISIBLE
            primaryButton.text = primary.orEmpty()
            primaryButton.isEnabled = true
            primaryButton.setOnClickListener { action() }
            alternateButton.text = secondary.orEmpty()
            alternateButton.setOnClickListener { extra() }
        }
    }

    override fun onStart() {
        super.onStart()
        foreground = true
        observed = null
        if (RecoveryPrefs.enabled(this)) {
            RecoveryScheduler.schedulePeriodic(this)
            RecoveryScheduler.registerWifiEvents(this)
        }
        render()
        val request = NetworkRequest.Builder().clearCapabilities()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val callback = if (Build.VERSION.SDK_INT >= 31)
            object : ConnectivityManager.NetworkCallback(
                ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
            ) {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    onNetworkChanged(network, capabilities)
                }
            } else object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    onNetworkChanged(network, capabilities)
                }
            }
        try {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .registerNetworkCallback(request, callback)
            networkMonitor = callback
        } catch (_: Exception) { networkMonitor = null }
        val last = getSharedPreferences("profile", MODE_PRIVATE)
            .getLong("last_checked", 0L)
        if (LiveSessionTest.selectedWifiMatches(this) &&
            System.currentTimeMillis() - last > 120_000L)
            ui.postDelayed({ if (foreground) runCheck(false) }, 650L)
    }

    private fun onNetworkChanged(network: Network, capabilities: NetworkCapabilities) {
        val state = when {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "online"
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) -> "portal"
            else -> "unknown"
        }
        ui.post {
            if (!foreground) return@post
            val snap = WifiProbe.current(this, network) ?: return@post
            if (TrustedNetworks.find(this, snap) == null) return@post
            val key = network.toString() + "|" + state
            if (key == lastNetworkKey) return@post
            lastNetworkKey = key
            when (state) {
                "online" -> {
                    observed = InternetVerifier.State.ONLINE
                    render()
                }
                "portal" -> if (RecoveryPrefs.enabled(this) &&
                    System.currentTimeMillis() - lastPortalEvent > 90_000L) {
                    lastPortalEvent = System.currentTimeMillis()
                    pendingRecovery?.let { ui.removeCallbacks(it) }
                    val task = Runnable { if (foreground) runCheck(true) }
                    pendingRecovery = task
                    ui.postDelayed(task, 2300L)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::headline.isInitialized) render()
    }

    override fun onStop() {
        foreground = false
        pendingRecovery?.let { ui.removeCallbacks(it) }
        pendingRecovery = null
        networkMonitor?.let {
            try {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        networkMonitor = null
        super.onStop()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
