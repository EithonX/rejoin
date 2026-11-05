package org.campus.autoconnect

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native day-to-day UI. No WebView, analytics, persistent foreground service,
 * or third-party UI dependencies.
 */
class MainActivity : Activity() {
    private val bg = Color.rgb(10, 19, 32)
    private val card = Color.rgb(21, 35, 53)
    private val card2 = Color.rgb(25, 43, 62)
    private val white = Color.rgb(238, 248, 251)
    private val muted = Color.rgb(147, 173, 187)
    private val accent = Color.rgb(58, 223, 189)
    private val amber = Color.rgb(255, 191, 102)

    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var eventAction: Runnable? = null
    private var networkState: String? = null
    private var lastEventAt = 0L
    private var foreground = false
    private var changingSwitch = false
    private var currentResult: InternetVerifier.State? = null

    private lateinit var statusTitle: TextView
    private lateinit var statusDescription: TextView
    private lateinit var statusDot: TextView
    private lateinit var networkText: TextView
    private lateinit var accountText: TextView
    private lateinit var scheduleText: TextView
    private lateinit var lastCheckedText: TextView
    private lateinit var lastRestoredText: TextView
    private lateinit var lastResultText: TextView
    private lateinit var historyItems: LinearLayout
    private lateinit var autoSwitch: Switch
    private lateinit var toolsPanel: LinearLayout
    private lateinit var toolsToggle: TextView

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun shape(color: Int, radius: Int = 22, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun gradient(first: Int, second: Int, radius: Int = 24): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(first, second)).apply {
            cornerRadius = dp(radius).toFloat()
        }

    private fun text(
        parent: LinearLayout,
        value: String,
        size: Float = 15f,
        tint: Int = white,
        bold: Boolean = false,
        top: Int = 0
    ): TextView = TextView(this).apply {
        setText(value)
        textSize = size
        setTextColor(tint)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        includeFontPadding = true
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(top)
        parent.addView(this, lp)
    }

    private fun card(parent: LinearLayout, top: Int = 13, hero: Boolean = false): LinearLayout {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(19), dp(19), dp(19), dp(19))
            background = if (hero) gradient(Color.rgb(24, 68, 77), Color.rgb(19, 41, 65))
                else shape(card, 23, Color.rgb(40, 61, 77))
        }
        val lp = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
        parent.addView(content, lp)
        return content
    }

    private fun row(parent: LinearLayout, top: Int = 0): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = dp(top)
            parent.addView(this, lp)
        }

    private fun action(
        value: String,
        primary: Boolean = false,
        compact: Boolean = false,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {
        text = value
        textSize = if (compact) 13f else 15f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setTextColor(if (primary) bg else white)
        gravity = Gravity.CENTER
        minHeight = dp(if (compact) 42 else 52)
        setPadding(dp(if (compact) 12 else 15), dp(9), dp(if (compact) 12 else 15), dp(9))
        background = if (primary) gradient(accent, Color.rgb(36, 191, 202), 15)
            else shape(card2, 15, Color.rgb(56, 78, 94))
        isFocusable = true
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun appendAction(parent: LinearLayout, value: String, primary: Boolean = false, top: Int = 9, onClick: () -> Unit) {
        parent.addView(action(value, primary, false, onClick),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        val scrolling = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            clipToPadding = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(19), dp(19), dp(19), dp(28))
        }
        scrolling.addView(content)
        setContentView(scrolling)

        // Respect system bars on Android versions that enforce edge-to-edge.
        scrolling.setOnApplyWindowInsetsListener { view, insets ->
            val top = if (Build.VERSION.SDK_INT >= 30)
                insets.getInsets(WindowInsets.Type.statusBars()).top else insets.systemWindowInsetTop
            val bottom = if (Build.VERSION.SDK_INT >= 30)
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom else insets.systemWindowInsetBottom
            view.setPadding(0, top, 0, bottom)
            insets
        }

        val header = row(content)
        val mark = TextView(this).apply {
            text = "C"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(bg)
            gravity = Gravity.CENTER
            background = shape(accent, 15)
        }
        header.addView(mark, LinearLayout.LayoutParams(dp(47), dp(47)))
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        text(brand, "CAMPUS  /  CONNECT", 10f, accent, true)
        text(brand, "AutoConnect", 23f, white, true)
        val version = TextView(this).apply {
            text = "v1.0"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
        }
        header.addView(version, LinearLayout.LayoutParams(dp(45), dp(40)))

        val hero = card(content, 20, true)
        val heroTop = row(hero)
        statusDot = TextView(this).apply {
            text = "●"
            textSize = 15f
            setTextColor(accent)
        }
        heroTop.addView(statusDot)
        val overline = TextView(this).apply {
            text = "  LIVE CONNECTION"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(177, 223, 227))
        }
        heroTop.addView(overline)
        statusTitle = text(hero, "Ready to check", 28f, white, true, 12)
        statusDescription = text(hero, "Your connection will be checked on the selected Wi-Fi.", 14f,
            Color.rgb(193, 222, 226), false, 7)
        val summaryBar = row(hero, 17)
        networkText = TextView(this).apply {
            text = "No trusted Wi-Fi yet"
            textSize = 13f
            setTextColor(Color.rgb(204, 235, 232))
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        summaryBar.addView(networkText, LinearLayout.LayoutParams(0, -2, 1f))
        val localTag = TextView(this).apply {
            text = "ON-DEVICE"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = shape(Color.rgb(23, 79, 79), 8)
        }
        summaryBar.addView(localTag)

        val actionRow = row(content, 13)
        val check = action("Check now") { doAction("check") }
        val repair = action("Repair connection", true) { doAction("repair") }
        actionRow.addView(check, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(5) })
        actionRow.addView(repair, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5) })

        val automatic = card(content)
        val automaticTop = row(automatic)
        val automaticTitle = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        automaticTop.addView(automaticTitle, LinearLayout.LayoutParams(0, -2, 1f))
        text(automaticTitle, "Automatic recovery", 18f, white, true)
        text(automaticTitle, "Silent when you're connected", 12f, muted, false, 4)
        autoSwitch = Switch(this).apply {
            contentDescription = "Automatic recovery switch"
            thumbTintList = ColorStateList.valueOf(accent)
            trackTintList = ColorStateList.valueOf(Color.rgb(44, 119, 113))
        }
        automaticTop.addView(autoSwitch)
        text(automatic, "Foreground: reacts to Wi-Fi changes. Background: Android runs a low-frequency check when permitted.",
            13f, muted, false, 14)
        scheduleText = text(automatic, "Background schedule not active", 12f, accent, false, 9)
        autoSwitch.setOnCheckedChangeListener { _, enabled ->
            if (!changingSwitch) toggleAutomatic(enabled)
        }

        val stats = card(content)
        text(stats, "SESSION ACTIVITY", 11f, accent, true)
        val lastCheckGroup = row(stats, 12)
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lastCheckGroup.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        lastCheckGroup.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        text(left, "Last checked", 12f, muted)
        lastCheckedText = text(left, "—", 16f, white, true, 4)
        text(right, "Last restored", 12f, muted)
        lastRestoredText = text(right, "—", 16f, white, true, 4)
        lastResultText = text(stats, "No recent checks", 12f, muted, false, 12)
        lastResultText.maxLines = 3
        lastResultText.ellipsize = TextUtils.TruncateAt.END
        lastResultText.setOnClickListener {
            val detail = getSharedPreferences("profile", MODE_PRIVATE)
                .getString("last_result", "No result") ?: "No result"
            AlertDialog.Builder(this).setTitle("Latest connection result")
                .setMessage(detail).setPositiveButton("Close", null).show()
        }

        val wifiCard = card(content)
        text(wifiCard, "TRUSTED NETWORK", 11f, accent, true)
        text(wifiCard, "Only your selected router", 18f, white, true, 9)
        text(wifiCard, "The app won't submit credentials on other Wi-Fi networks.",
            13f, muted, false, 4)
        appendAction(wifiCard, "Select current Wi-Fi", false, 12) { selectNetwork() }

        val accountCard = card(content)
        text(accountCard, "ACCOUNT & PRIVACY", 11f, accent, true)
        accountText = text(accountCard, "No credentials saved", 17f, white, true, 10)
        text(accountCard, "Encrypted on this device. CU's current HTTP login is not encrypted in transit.",
            12f, muted, false, 6)
        val securityButtons = row(accountCard, 12)
        val configure = action("Update credentials", false, true) { credentialsDialog() }
        val permissions = action("App permissions", false, true) { openPermissions() }
        securityButtons.addView(configure, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(4) })
        securityButtons.addView(permissions, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(4) })

        val history = card(content)
        text(history, "RECENT ACTIVITY", 11f, accent, true)
        historyItems = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        history.addView(historyItems, LinearLayout.LayoutParams(-1, -2))

        val tools = card(content)
        toolsToggle = action("Advanced tools & diagnostics   +", false, true) {
            toolsPanel.visibility = if (toolsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            toolsToggle.text = if (toolsPanel.visibility == View.VISIBLE)
                "Advanced tools & diagnostics   −" else "Advanced tools & diagnostics   +"
        }
        tools.addView(toolsToggle, LinearLayout.LayoutParams(-1, -2))
        toolsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        tools.addView(toolsPanel)
        text(toolsPanel, "These tests affect the shared CU session. They are not needed for daily operation.",
            13f, muted, false, 12)
        appendAction(toolsPanel, "Log out shared session", false) { confirmLogout() }
        appendAction(toolsPanel, "Sign in once using saved credentials", false) { confirmManualLogin() }
        appendAction(toolsPanel, "Verify internet (IP-only)", false) { doAction("check") }
        appendAction(toolsPanel, "Open official CU login page", false) { openPortal() }
        appendAction(toolsPanel, "Erase saved credentials", false) { confirmErase() }

        text(content, "Private by design  •  No analytics  •  Native Android",
            11f, muted, false, 20).gravity = Gravity.CENTER
        refreshDashboard()
    }

    private fun toggleAutomatic(enabled: Boolean) {
        val p = getSharedPreferences("profile", MODE_PRIVATE)
        if (enabled) {
            val trusted = !p.getString("ssid", null).isNullOrBlank()
            if (!trusted || !CredentialVault.hasCredentials(this) ||
                !p.getBoolean("http_opt_in", false)) {
                currentResult = null
                statusDescription.text = "Choose your router and save your credentials first."
                changingSwitch = true
                autoSwitch.isChecked = false
                changingSwitch = false
                credentialsDialogIfNeeded()
                return
            }
            val result = ProbeJobService.schedule(this)
            p.edit().putBoolean("auto_enabled", result).apply()
            ActivityHistory.record(this, if (result) "Automatic recovery enabled" else "Scheduling unavailable")
        } else {
            p.edit().putBoolean("auto_enabled", false).apply()
            ProbeJobService.cancel(this)
            ActivityHistory.record(this, "Automatic recovery paused")
        }
        refreshDashboard()
    }

    private fun credentialsDialogIfNeeded() {
        val p = getSharedPreferences("profile", MODE_PRIVATE)
        if (!CredentialVault.hasCredentials(this) || !p.getBoolean("http_opt_in", false)) credentialsDialog()
    }

    private fun credentialsDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(7), dp(22), 0)
        }
        val uid = EditText(this).apply {
            hint = "University UID"
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
        }
        val pwd = EditText(this).apply {
            hint = "University internet password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        val consent = CheckBox(this).apply {
            text = "I understand CU's observed HTTP authentication sends passwords without TLS encryption. I authorize one-device automatic login."
            textSize = 13f
        }
        container.addView(uid)
        container.addView(pwd)
        container.addView(consent)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Set up CU account")
            .setMessage("Your credentials are encrypted on this phone, never uploaded to another server by the app.")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!consent.isChecked || uid.text.isBlank() || pwd.text.isBlank()) {
                    consent.error = "Complete both fields and accept the HTTP warning"
                    return@setOnClickListener
                }
                if (!CredentialVault.save(this, uid.text.toString(), pwd.text.toString())) {
                    statusDescription.text = "Could not save encrypted credentials."
                    return@setOnClickListener
                }
                getSharedPreferences("profile", MODE_PRIVATE).edit()
                    .putBoolean("http_opt_in", true).apply()
                pwd.text.clear()
                ActivityHistory.record(this, "Account credentials updated")
                dialog.dismiss()
                refreshDashboard()
            }
        }
        dialog.show()
    }

    private fun selectNetwork() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 44)
            return
        }
        val snapshot = WifiProbe.current(this)
        if (snapshot == null) {
            AlertDialog.Builder(this).setTitle("Wi-Fi unavailable")
                .setMessage("Connect to your own router and enable Location before selecting it.")
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Trust this router?")
            .setMessage("SSID: " + snapshot.ssid + "\nBSSID: " + snapshot.bssid +
                "\n\nAutomatic login will be restricted to this exact Wi-Fi access point.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Trust router") { _, _ ->
                WifiProbe.trust(this, snapshot)
                ActivityHistory.record(this, "Trusted router selected")
                refreshDashboard()
                doAction("check")
            }.show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Log out the shared CU session?")
            .setMessage("Everyone connected through your router may temporarily lose internet. This will not automatically sign you back in.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Log out") { _, _ ->
                getSharedPreferences("profile", MODE_PRIVATE).edit()
                    .putLong("manual_test_until", System.currentTimeMillis() + 10 * 60 * 1000L)
                    .apply()
                doAction("logout")
            }.show()
    }

    private fun confirmManualLogin() {
        AlertDialog.Builder(this)
            .setTitle("Sign in once?")
            .setMessage("Uses your encrypted credentials and makes one authorized request to the CU HTTP portal.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Sign in") { _, _ -> doAction("login") }
            .show()
    }

    private fun confirmErase() {
        AlertDialog.Builder(this)
            .setTitle("Erase saved account?")
            .setMessage("Stops automatic recovery and deletes encrypted credentials from this phone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Erase") { _, _ ->
                ProbeJobService.cancel(this)
                CredentialVault.clear(this)
                ActivityHistory.record(this, "Account credentials erased")
                refreshDashboard()
            }.show()
    }

    private fun openPortal() {
        if (!LiveSessionTest.selectedWifiMatches(this)) {
            statusDescription.text = "Connect to the trusted Wi-Fi first."
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW,
            Uri.parse("http://172.16.2.1:1000/login?")))
    }

    private fun openPermissions() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + packageName)))
    }

    private fun doAction(kind: String) {
        if (!busy.compareAndSet(false, true)) return
        statusTitle.text = when (kind) {
            "repair", "login" -> "Connecting..."
            "logout" -> "Signing out..."
            else -> "Checking..."
        }
        statusDescription.text = "Working only on your selected Wi-Fi."
        executor.execute {
            try {
                var result = ""
                var checked: InternetVerifier.State? = null
                when (kind) {
                    "check" -> {
                        val r = InternetVerifier.check(applicationContext)
                        checked = r.state
                        result = r.state.name + ": " + r.details
                        val p = getSharedPreferences("profile", MODE_PRIVATE)
                        p.edit().putString("last_result", result)
                            .putLong("last_checked", System.currentTimeMillis()).apply()
                        ActivityHistory.record(applicationContext, when (r.state) {
                            InternetVerifier.State.ONLINE -> "Connection healthy"
                            InternetVerifier.State.PORTAL -> "CU sign-in required"
                            InternetVerifier.State.UNKNOWN -> "Connection check inconclusive"
                        })
                        if (r.state == InternetVerifier.State.ONLINE)
                            NetworkStatusHint.requestAfterVerifiedOnline(applicationContext)
                    }
                    "repair", "login" -> {
                        result = SessionEngine.run(
                            applicationContext, userInitiated = true,
                            explicitlyLoggedOut = kind == "login"
                        ) { step -> main.post { statusDescription.text = step.take(150) } }
                        checked = when {
                            result.contains("VERIFIED", true) ||
                                result.contains("Internet verified", true) ||
                                result.contains("online", true) -> InternetVerifier.State.ONLINE
                            result.contains("portal", true) -> InternetVerifier.State.PORTAL
                            else -> null
                        }
                    }
                    "logout" -> {
                        result = LiveSessionTest.logout(applicationContext)
                        ActivityHistory.record(applicationContext, "Shared session logout requested")
                        checked = InternetVerifier.State.PORTAL
                    }
                }
                val outcome = result
                val state = checked
                main.post {
                    currentResult = state
                    statusDescription.text = outcome.take(180)
                    busy.set(false)
                    refreshDashboard()
                }
            } catch (e: Exception) {
                main.post {
                    busy.set(false)
                    statusDescription.text = "Action stopped: " + e.javaClass.simpleName
                    refreshDashboard()
                }
            }
        }
    }

    private fun statusFromStored(): String {
        val p = getSharedPreferences("profile", MODE_PRIVATE)
        val value = p.getString("last_result", "").orEmpty()
        val age = System.currentTimeMillis() - p.getLong("last_checked", 0)
        if (age > 30 * 60 * 1000L || age < 0) return "stale"
        return when {
            value.startsWith("ONLINE") || value.contains("LOGIN VERIFIED", true) ||
                value.contains("Internet verified", true) ||
                value.contains("Internet online", true) ||
                value.contains("already online", true) -> "online"
            value.startsWith("PORTAL") || value.contains("CU portal detected", true) ||
                value.contains("CAPTIVE PORTAL", true) -> "portal"
            else -> "unknown"
        }
    }

    private fun refreshDashboard() {
        if (!::statusTitle.isInitialized) return
        val p = getSharedPreferences("profile", MODE_PRIVATE)
        val trusted = p.getString("ssid", null)
        val matched = LiveSessionTest.selectedWifiMatches(this)
        networkText.text = if (trusted == null) "No trusted network"
            else if (matched) "●  " + trusted + "  ·  trusted"
            else trusted + "  ·  not connected"
        accountText.text = if (CredentialVault.hasCredentials(this)) "Credentials saved securely"
            else "Set up your CU account"

        val auto = p.getBoolean("auto_enabled", false)
        val scheduled = ProbeJobService.enabled(this)
        changingSwitch = true
        autoSwitch.isChecked = auto
        changingSwitch = false
        scheduleText.text = when {
            auto && scheduled -> "System checks: about every 15 min · timing not guaranteed"
            auto -> "Recovery enabled, but background job is missing"
            else -> "Automatic recovery is off"
        }

        val current = currentResult
        val state = if (current != null) when (current) {
            InternetVerifier.State.ONLINE -> "online"
            InternetVerifier.State.PORTAL -> "portal"
            InternetVerifier.State.UNKNOWN -> "unknown"
        } else statusFromStored()

        val label: String
        val description: String
        val dot: Int
        when {
            trusted == null -> {
                label = "Welcome aboard"
                description = "Select your router, save CU credentials and enable recovery."
                dot = muted
            }
            !matched -> {
                label = "Away from campus Wi-Fi"
                description = "Credentials stay inactive on other networks."
                dot = muted
            }
            state == "online" -> {
                label = "You're connected"
                description = "Internet access verified on your trusted Wi-Fi."
                dot = accent
            }
            state == "portal" -> {
                label = "Sign-in required"
                description = "CU authentication is needed. Automatic recovery can help."
                dot = amber
            }
            state == "unknown" -> {
                label = "Checking availability"
                description = "Connection status is inconclusive. Tap Check now."
                dot = amber
            }
            else -> {
                label = "Ready to check"
                description = "Tap Check now for a fresh connectivity result."
                dot = muted
            }
        }
        if (!busy.get()) {
            statusTitle.text = label
            statusDescription.text = description
        }
        statusDot.setTextColor(dot)
        val lastChecked = p.getLong("last_checked", 0L)
        val lastRestored = p.getLong("last_success", 0L)
        lastCheckedText.text = if (lastChecked > 0)
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastChecked)) else "Never"
        lastRestoredText.text = if (lastRestored > 0)
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastRestored))
            else "Not yet"
        lastResultText.text = p.getString("last_result", null)
            ?.take(210)?.ifBlank { "No recent checks" } ?: "No recent checks"
        historyItems.removeAllViews()
        val entries = ActivityHistory.latest(this)
        if (entries.isEmpty()) {
            text(historyItems, "No activity recorded yet", 13f, muted, false, 12)
        } else {
            for (entry in entries) {
                val line = row(historyItems, 12)
                val mark = TextView(this).apply {
                    text = "●"
                    textSize = 10f
                    setTextColor(accent)
                }
                line.addView(mark, LinearLayout.LayoutParams(dp(20), -2))
                val event = TextView(this).apply {
                    text = entry.event
                    textSize = 13f
                    setTextColor(white)
                }
                line.addView(event, LinearLayout.LayoutParams(0, -2, 1f))
                val whenText = TextView(this).apply {
                    text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.time))
                    textSize = 12f
                    setTextColor(muted)
                }
                line.addView(whenText)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        foreground = true
        refreshDashboard()
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val monitor = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val state = when {
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "online"
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) -> "portal"
                    else -> "unknown"
                }
                main.post {
                    if (!foreground) return@post
                    val marker = network.toString() + ":" + state
                    if (marker == networkState) return@post
                    networkState = marker
                    if (state == "online" && LiveSessionTest.selectedWifiMatches(this@MainActivity)) {
                        currentResult = InternetVerifier.State.ONLINE
                        refreshDashboard()
                    } else if (state == "portal" &&
                        getSharedPreferences("profile", MODE_PRIVATE)
                            .getBoolean("auto_enabled", false) &&
                        System.currentTimeMillis() - lastEventAt > 90000L) {
                        lastEventAt = System.currentTimeMillis()
                        eventAction?.let { main.removeCallbacks(it) }
                        val task = Runnable { if (foreground) doAction("repair") }
                        eventAction = task
                        main.postDelayed(task, 2400L)
                    }
                }
            }
            override fun onLost(network: Network) {
                main.post {
                    if (foreground) {
                        currentResult = null
                        networkState = null
                        refreshDashboard()
                    }
                }
            }
        }
        try {
            cm.registerNetworkCallback(request, monitor)
            callback = monitor
        } catch (_: Exception) { callback = null }

        val checkedAt = getSharedPreferences("profile", MODE_PRIVATE)
            .getLong("last_checked", 0L)
        if (System.currentTimeMillis() - checkedAt > 90000L &&
            LiveSessionTest.selectedWifiMatches(this) && !busy.get()) {
            main.postDelayed({ if (foreground) doAction("check") }, 650L)
        }
    }

    override fun onStop() {
        foreground = false
        eventAction?.let { main.removeCallbacks(it) }
        eventAction = null
        callback?.let {
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        callback = null
        super.onStop()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 44 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            selectNetwork()
        } else {
            refreshDashboard()
        }
    }
}
