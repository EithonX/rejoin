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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Home is intentionally quiet: status, recovery, a switch, and recent activity. */
class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var monitor: ConnectivityManager.NetworkCallback? = null
    private var pendingRepair: Runnable? = null
    private var foreground = false
    private var suppressSwitch = false
    private var lastNetworkEvent = 0L
    private var lastNetworkSignature: String? = null
    private var liveState: InternetVerifier.State? = null

    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var statusTag: TextView
    private lateinit var networkName: TextView
    private lateinit var savedLogin: TextView
    private lateinit var primaryAction: TextView
    private lateinit var secondaryAction: TextView
    private lateinit var automaticSwitch: Switch
    private lateinit var scheduleInfo: TextView
    private lateinit var lastCheck: TextView
    private lateinit var lastRecovery: TextView
    private lateinit var recent: LinearLayout
    private lateinit var setupHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(RejoinUi.canvas)
        val outer = RejoinUi.column(this).apply { setBackgroundColor(RejoinUi.canvas) }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setBackgroundColor(RejoinUi.canvas)
        }
        val body = RejoinUi.column(this).apply {
            setPadding(RejoinUi.dp(this@MainActivity, 22), RejoinUi.dp(this@MainActivity, 24),
                RejoinUi.dp(this@MainActivity, 22), RejoinUi.dp(this@MainActivity, 24))
        }
        scroll.addView(body)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottomNav = createNavigation()
        outer.addView(bottomNav, LinearLayout.LayoutParams(-1, -2))
        setContentView(outer)
        RejoinUi.applySystemBars(this, outer)

        // Wordmark, not a marketing banner.
        val top = RejoinUi.row(this)
        top.addView(RejoinUi.icon(this, R.drawable.ic_rejoin_mark, 38),
            LinearLayout.LayoutParams(RejoinUi.dp(this, 38), RejoinUi.dp(this, 38)))
        val wordmark = RejoinUi.label(this, "rejoin", 27f, RejoinUi.ink, true)
        top.addView(wordmark, LinearLayout.LayoutParams(0, -2, 1f).apply {
            leftMargin = RejoinUi.dp(this@MainActivity, 10)
        })
        val headerMeta = RejoinUi.label(this, "CAMPUS WI-FI", 10f, RejoinUi.muted, true).apply {
            letterSpacing = 0.10f
        }
        top.addView(headerMeta)
        RejoinUi.add(body, top)

        val kicker = RejoinUi.label(this, "Stay connected, quietly.", 13f, RejoinUi.muted)
        RejoinUi.add(body, kicker, 7)

        val hero = RejoinUi.inset(this, RejoinUi.jadeDeep).apply {
            setPadding(RejoinUi.dp(this@MainActivity, 23), RejoinUi.dp(this@MainActivity, 24),
                RejoinUi.dp(this@MainActivity, 23), RejoinUi.dp(this@MainActivity, 23))
            background = RejoinUi.surface(this@MainActivity, RejoinUi.jadeDeep, 27)
        }
        RejoinUi.add(body, hero, 25)
        val heroHead = RejoinUi.row(this)
        heroHead.addView(RejoinUi.label(this, "CONNECTION", 11f,
            RejoinUi.mint, true).apply { letterSpacing = 0.17f },
            LinearLayout.LayoutParams(0, -2, 1f))
        statusTag = RejoinUi.label(this, "CHECKING", 10f, RejoinUi.jadeDeep, true).apply {
            gravity = Gravity.CENTER
            setPadding(RejoinUi.dp(this@MainActivity, 11), RejoinUi.dp(this@MainActivity, 6),
                RejoinUi.dp(this@MainActivity, 11), RejoinUi.dp(this@MainActivity, 6))
            background = RejoinUi.surface(this@MainActivity, RejoinUi.mint, 24)
        }
        heroHead.addView(statusTag)
        RejoinUi.add(hero, heroHead)
        statusTitle = RejoinUi.label(this, "Ready when you are.", 29f, RejoinUi.white, true)
        RejoinUi.add(hero, statusTitle, 29)
        statusSubtitle = RejoinUi.label(this, "Checking your trusted connection.", 14f,
            RejoinUi.mint)
        statusSubtitle.setLineSpacing(RejoinUi.dp(this, 3).toFloat(), 1f)
        RejoinUi.add(hero, statusSubtitle, 9)
        val heroDivider = View(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(61, 112, 95))
        }
        RejoinUi.add(hero, heroDivider, 22)
        heroDivider.layoutParams.height = RejoinUi.dp(this, 1)
        val trustedLine = RejoinUi.row(this)
        trustedLine.addView(RejoinUi.icon(this, R.drawable.ic_wifi, 19, RejoinUi.mint))
        networkName = RejoinUi.label(this, "No network selected", 13f, RejoinUi.mint).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        trustedLine.addView(networkName, LinearLayout.LayoutParams(0, -2, 1f).apply {
            leftMargin = RejoinUi.dp(this@MainActivity, 9)
        })
        RejoinUi.add(hero, trustedLine, 17)
        savedLogin = RejoinUi.label(this, "No login saved", 12f, RejoinUi.mint)
        RejoinUi.add(hero, savedLogin, 9)

        primaryAction = RejoinUi.action(this, "Set up Rejoin") {}
        RejoinUi.add(body, primaryAction, 15)
        secondaryAction = RejoinUi.smallAction(this, "Check connection") {
            execute("check")
        }.apply { gravity = Gravity.CENTER }
        RejoinUi.add(body, secondaryAction, 7)

        setupHint = RejoinUi.label(this, "Select your Wi-Fi and save your login in Settings to begin.",
            12f, RejoinUi.amber)
        RejoinUi.add(body, setupHint, 12)

        val autoPanel = RejoinUi.inset(this)
        RejoinUi.add(body, autoPanel, 23)
        val autoHeader = RejoinUi.row(this)
        val headings = RejoinUi.column(this)
        autoHeader.addView(headings, LinearLayout.LayoutParams(0, -2, 1f))
        RejoinUi.add(headings, RejoinUi.label(this, "Automatic recovery", 17f, RejoinUi.ink, true))
        RejoinUi.add(headings, RejoinUi.label(this, "Only on your trusted Wi-Fi", 12f, RejoinUi.muted), 6)
        automaticSwitch = Switch(this).apply {
            thumbTintList = ColorStateList.valueOf(RejoinUi.jade)
            trackTintList = ColorStateList.valueOf(RejoinUi.mint)
            contentDescription = "Enable automatic recovery"
        }
        autoHeader.addView(automaticSwitch)
        RejoinUi.add(autoPanel, autoHeader)
        val rule = RejoinUi.rule(this)
        RejoinUi.add(autoPanel, rule, 19)
        scheduleInfo = RejoinUi.label(this, "Balanced · background checks when needed", 12f, RejoinUi.muted)
        RejoinUi.add(autoPanel, scheduleInfo, 14)
        automaticSwitch.setOnCheckedChangeListener { _, checked ->
            if (!suppressSwitch) toggleAuto(checked)
        }

        val overview = RejoinUi.inset(this)
        RejoinUi.add(body, overview, 15)
        RejoinUi.add(overview, RejoinUi.label(this, "AT A GLANCE", 11f, RejoinUi.muted, true)
            .apply { letterSpacing = 0.13f })
        val metrics = RejoinUi.row(this)
        val left = RejoinUi.column(this)
        val right = RejoinUi.column(this)
        metrics.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        metrics.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        RejoinUi.add(left, RejoinUi.label(this, "Last checked", 12f, RejoinUi.muted))
        lastCheck = RejoinUi.label(this, "—", 15f, RejoinUi.ink, true)
        RejoinUi.add(left, lastCheck, 7)
        RejoinUi.add(right, RejoinUi.label(this, "Last rejoined", 12f, RejoinUi.muted))
        lastRecovery = RejoinUi.label(this, "—", 15f, RejoinUi.ink, true)
        RejoinUi.add(right, lastRecovery, 7)
        RejoinUi.add(overview, metrics, 18)

        val activities = RejoinUi.inset(this)
        RejoinUi.add(body, activities, 15)
        val actHead = RejoinUi.row(this)
        actHead.addView(RejoinUi.label(this, "Recent activity", 16f, RejoinUi.ink, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        actHead.addView(RejoinUi.smallAction(this, "View all  →") {
            startActivity(Intent(this, ActivityLogActivity::class.java))
        })
        RejoinUi.add(activities, actHead)
        recent = RejoinUi.column(this)
        RejoinUi.add(activities, recent, 9)

        val footer = RejoinUi.label(this, "No ads. No tracking. Credentials stay on this device.",
            11f, RejoinUi.muted).apply { gravity = Gravity.CENTER }
        RejoinUi.add(body, footer, 23)
        render()
    }

    private fun createNavigation(): LinearLayout {
        val wrap = RejoinUi.column(this).apply {
            setBackgroundColor(RejoinUi.white)
            addView(View(this@MainActivity).apply { setBackgroundColor(RejoinUi.line) },
                LinearLayout.LayoutParams(-1, RejoinUi.dp(this@MainActivity, 1)))
        }
        val nav = RejoinUi.row(this).apply {
            setPadding(RejoinUi.dp(this@MainActivity, 13), RejoinUi.dp(this@MainActivity, 10),
                RejoinUi.dp(this@MainActivity, 13), RejoinUi.dp(this@MainActivity, 10))
        }
        fun item(title: String, drawable: Int, active: Boolean, onTap: () -> Unit): LinearLayout {
            val container = RejoinUi.column(this).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setPadding(0, RejoinUi.dp(this@MainActivity, 3), 0, RejoinUi.dp(this@MainActivity, 3))
                setOnClickListener { onTap() }
            }
            val color = if (active) RejoinUi.jade else RejoinUi.muted
            container.addView(RejoinUi.icon(this, drawable, 21, color))
            val label = RejoinUi.label(this, title, 11f, color, active).apply {
                gravity = Gravity.CENTER
            }
            RejoinUi.add(container, label, 5)
            return container
        }
        nav.addView(item("Home", R.drawable.ic_home, true) {}, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(item("Activity", R.drawable.ic_activity, false) {
            startActivity(Intent(this, ActivityLogActivity::class.java))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(item("Settings", R.drawable.ic_settings, false) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        wrap.addView(nav)
        return wrap
    }

    private fun toggleAuto(enabled: Boolean) {
        val p = getSharedPreferences("profile", MODE_PRIVATE)
        if (enabled) {
            val allowed = TrustedNetworks.all(this).any { CredentialVault.hasLoginFor(this, it) }
            if (!allowed || !p.getBoolean("http_opt_in", false)) {
                suppressSwitch = true
                automaticSwitch.isChecked = false
                suppressSwitch = false
                startActivity(Intent(this, SettingsActivity::class.java))
                return
            }
            p.edit().putBoolean("auto_enabled", true).commit()
            RecoveryScheduler.registerWifiEvents(this)
            if (!RecoveryScheduler.schedule(this, 30_000L)) {
                p.edit().putBoolean("auto_enabled", false).commit()
                automaticSwitch.isChecked = false
            } else ActivityHistory.record(this, "Automatic recovery enabled")
        } else {
            p.edit().putBoolean("auto_enabled", false).commit()
            RecoveryScheduler.disable(this)
            ActivityHistory.record(this, "Automatic recovery paused")
        }
        render()
    }

    private fun execute(which: String) {
        if (!busy.compareAndSet(false, true)) return
        statusTitle.text = if (which == "repair") "Reconnecting…" else "Checking…"
        statusSubtitle.text = "Working only through your selected Wi-Fi."
        executor.execute {
            try {
                val outcome: String
                val state: InternetVerifier.State
                if (which == "check") {
                    val checked = InternetVerifier.check(applicationContext)
                    state = checked.state
                    outcome = checked.details
                    val currentProfile = WifiProbe.current(applicationContext)?.let {
                        TrustedNetworks.find(applicationContext, it)
                    }
                    getSharedPreferences("profile", MODE_PRIVATE).edit()
                        .putString("last_profile_id", currentProfile?.id)
                        .putString("last_result", when (state) {
                            InternetVerifier.State.ONLINE -> "Online — " + outcome
                            InternetVerifier.State.PORTAL -> "Portal detected — " + outcome
                            InternetVerifier.State.UNKNOWN -> "Unknown — " + outcome
                        }).putLong("last_checked", System.currentTimeMillis())
                        .putString("last_class", when (state) {
                            InternetVerifier.State.ONLINE -> "healthy"
                            InternetVerifier.State.PORTAL -> "portal"
                            InternetVerifier.State.UNKNOWN -> "unknown"
                        }).apply()
                    ActivityHistory.record(applicationContext, when (state) {
                        InternetVerifier.State.ONLINE -> "Connection checked · online"
                        InternetVerifier.State.PORTAL -> "CU sign-in needed"
                        InternetVerifier.State.UNKNOWN -> "Connection check inconclusive"
                    })
                    if (state == InternetVerifier.State.ONLINE)
                        NetworkStatusHint.requestAfterVerifiedOnline(applicationContext)
                } else {
                    outcome = SessionEngine.run(
                        applicationContext, userInitiated = true,
                        progress = { step ->
                            main.post { if (busy.get()) statusSubtitle.text = step }
                        }
                    )
                    state = when {
                        outcome.contains("LOGIN VERIFIED", true) ||
                            outcome.contains("Internet online", true) -> InternetVerifier.State.ONLINE
                        outcome.contains("portal", true) -> InternetVerifier.State.PORTAL
                        else -> InternetVerifier.State.UNKNOWN
                    }
                }
                main.post {
                    busy.set(false)
                    liveState = state
                    render()
                    if (state == InternetVerifier.State.UNKNOWN && which == "repair") {
                        statusSubtitle.text = outcome.take(125)
                    }
                }
            } catch (e: Exception) {
                main.post {
                    busy.set(false)
                    liveState = InternetVerifier.State.UNKNOWN
                    render()
                    statusSubtitle.text = "Check unavailable (" + e.javaClass.simpleName + ")"
                }
            }
        }
    }

    private fun render() {
        if (!::statusTitle.isInitialized) return
        val prefs = getSharedPreferences("profile", MODE_PRIVATE)
        val trusted = TrustedNetworks.all(this)
        val snapshot = WifiProbe.current(this)
        val profile = snapshot?.let { TrustedNetworks.find(this, it) }
        val accountSaved = profile?.let { CredentialVault.hasLoginFor(this, it) } ?: false
        val allConfigured = trusted.isNotEmpty() &&
            trusted.any { CredentialVault.hasLoginFor(this, it) } &&
            prefs.getBoolean("http_opt_in", false)

        networkName.text = when {
            profile != null -> profile.label
            trusted.isEmpty() -> "No saved Wi-Fi yet"
            else -> trusted.size.toString() + " trusted " +
                if (trusted.size == 1) "Wi-Fi saved" else "Wi-Fi networks saved"
        }
        savedLogin.text = when {
            profile != null && accountSaved -> "✓  Login saved on this device"
            profile != null -> "No login saved for this Wi-Fi"
            trusted.isEmpty() -> "Start by saving your Wi-Fi"
            else -> "Your saved Wi-Fi will be recognized automatically"
        }
        setupHint.visibility = if (allConfigured) View.GONE else View.VISIBLE
        setupHint.text = when {
            trusted.isEmpty() -> "Start with the Wi-Fi you're connected to."
            !allConfigured -> "Save your campus login once to finish setup."
            else -> ""
        }

        suppressSwitch = true
        automaticSwitch.isChecked = RecoveryPrefs.enabled(this)
        suppressSwitch = false
        val mode = if (RecoveryPrefs.mode(this) == RecoveryPolicy.Mode.BALANCED)
            "Balanced" else "Responsive"
        scheduleInfo.text = if (RecoveryPrefs.enabled(this))
            "On · " + mode + " protection for saved networks"
        else "Off · enable to rejoin automatically"

        val checked = prefs.getLong("last_checked", 0L)
        val lastSuccess = if (profile != null)
            prefs.getLong("last_success_" + profile.id, 0L)
        else prefs.getLong("last_success", 0L)
        lastCheck.text = if (checked > 0L)
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(checked)) else "Not yet"
        lastRecovery.text = if (lastSuccess > 0L)
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastSuccess)) else "Not yet"

        // A result verified on another router is not evidence this router is online.
        val recent = checked > 0L && System.currentTimeMillis() - checked <= 20 * 60_000L &&
            prefs.getString("last_profile_id", null) == profile?.id
        val current = liveState ?: when {
            recent && prefs.getString("last_class", "") in setOf("healthy", "restored") ->
                InternetVerifier.State.ONLINE
            recent && prefs.getString("last_class", "") == "portal" ->
                InternetVerifier.State.PORTAL
            else -> InternetVerifier.State.UNKNOWN
        }
        if (!busy.get()) {
            when {
                trusted.isEmpty() -> {
                    statusTitle.text = "Let's get you set up."
                    statusSubtitle.text = "Save your Wi-Fi and one login. That's all."
                    statusTag.text = "SETUP"
                }
                profile == null -> {
                    statusTitle.text = "Standing by."
                    statusSubtitle.text = "Rejoin works when you're on a saved Wi-Fi."
                    statusTag.text = "AWAY"
                }
                !accountSaved -> {
                    statusTitle.text = "Almost ready."
                    statusSubtitle.text = "Save a login for " + profile.label + "."
                    statusTag.text = "SETUP"
                }
                current == InternetVerifier.State.ONLINE -> {
                    statusTitle.text = "You're connected."
                    statusSubtitle.text = if (RecoveryPrefs.enabled(this))
                        "Protected. Rejoin will stay out of your way."
                    else "Internet is working. Automatic recovery is off."
                    statusTag.text = "ONLINE"
                }
                current == InternetVerifier.State.PORTAL -> {
                    statusTitle.text = "Sign-in needed."
                    statusSubtitle.text = "Your campus connection needs a fresh login."
                    statusTag.text = "REJOIN"
                }
                else -> {
                    statusTitle.text = "Ready when you are."
                    statusSubtitle.text = "Check the connection when you need to."
                    statusTag.text = "READY"
                }
            }
        }
        primaryAction.visibility = View.VISIBLE
        secondaryAction.visibility = View.VISIBLE
        val primaryText: String
        val action: () -> Unit
        when {
            trusted.isEmpty() -> {
                primaryText = "Set up Rejoin"
                action = { startActivity(Intent(this, SettingsActivity::class.java)) }
                secondaryAction.visibility = View.GONE
            }
            profile == null -> {
                primaryText = "Manage saved Wi-Fi"
                action = { startActivity(Intent(this, SettingsActivity::class.java)) }
                secondaryAction.visibility = View.GONE
            }
            !accountSaved -> {
                primaryText = "Save a login"
                action = { startActivity(Intent(this, LoginsActivity::class.java)) }
                secondaryAction.visibility = View.GONE
            }
            current == InternetVerifier.State.ONLINE -> {
                primaryText = ""
                action = {}
                primaryAction.visibility = View.GONE
                secondaryAction.text = "Check connection again"
                secondaryAction.setOnClickListener { execute("check") }
            }
            current == InternetVerifier.State.PORTAL -> {
                primaryText = "Reconnect"
                action = { execute("repair") }
                secondaryAction.text = "Check connection"
                secondaryAction.setOnClickListener { execute("check") }
            }
            else -> {
                primaryText = "Check connection"
                action = { execute("check") }
                secondaryAction.text = "Try reconnecting"
                secondaryAction.setOnClickListener { execute("repair") }
            }
        }
        if (primaryAction.visibility == View.VISIBLE) {
            primaryAction.text = primaryText
            primaryAction.isEnabled = !busy.get()
            primaryAction.setOnClickListener { action() }
        }
        recentList()

    }

    private fun recentList() {
        recent.removeAllViews()
        val items = ActivityHistory.latest(this, 2)
        if (items.isEmpty()) {
            RejoinUi.add(recent, RejoinUi.label(this,
                "Nothing to report. That's a good thing.", 13f, RejoinUi.muted), 8)
        } else for ((index, item) in items.withIndex()) {
            if (index != 0) RejoinUi.add(recent, RejoinUi.rule(this), 12)
            val row = RejoinUi.row(this)
            row.addView(RejoinUi.label(this, "•", 18f, RejoinUi.jade),
                LinearLayout.LayoutParams(RejoinUi.dp(this, 19), -2))
            row.addView(RejoinUi.label(this, item.event, 13f, RejoinUi.ink),
                LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(RejoinUi.label(this,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(item.time)),
                11f, RejoinUi.muted))
            RejoinUi.add(recent, row, 12)
        }
    }

    override fun onStart() {
        super.onStart()
        foreground = true
        liveState = null
        render()
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val req = NetworkRequest.Builder().clearCapabilities()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val cb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            object : ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                    handleNetworkChange(network, caps)
            } else object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                    handleNetworkChange(network, caps)
            }
        try {
            cm.registerNetworkCallback(req, cb)
            monitor = cb
        } catch (_: Exception) { monitor = null }

        val p = getSharedPreferences("profile", MODE_PRIVATE)
        if (LiveSessionTest.selectedWifiMatches(this) &&
            System.currentTimeMillis() - p.getLong("last_checked", 0L) > 2 * 60_000L) {
            main.postDelayed({ if (foreground) execute("check") }, 600L)
        }
    }

    private fun handleNetworkChange(network: Network, caps: NetworkCapabilities) {
        val state = when {
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "validated"
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) -> "portal"
            else -> "other"
        }
        main.post {
            if (!foreground) return@post
            val snapshot = WifiProbe.current(this, network) ?: return@post
            if (TrustedNetworks.find(this, snapshot) == null) return@post
            val signature = network.toString() + "|" + state
            if (signature == lastNetworkSignature) return@post
            lastNetworkSignature = signature
            if (state == "validated") {
                liveState = InternetVerifier.State.ONLINE
                render()
            } else if (state == "portal" && RecoveryPrefs.enabled(this) &&
                System.currentTimeMillis() - lastNetworkEvent > 90_000L) {
                lastNetworkEvent = System.currentTimeMillis()
                pendingRepair?.let { main.removeCallbacks(it) }
                val task = Runnable { if (foreground) execute("repair") }
                pendingRepair = task
                main.postDelayed(task, 2_200L)
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

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
