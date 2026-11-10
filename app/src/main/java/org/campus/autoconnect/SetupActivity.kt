package org.campus.autoconnect

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView

class SetupActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("profile", MODE_PRIVATE) }
    private lateinit var progress: LinearLayout
    private lateinit var stepCaption: TextView
    private lateinit var heading: TextView
    private lateinit var detail: TextView
    private lateinit var content: LinearLayout
    private lateinit var primary: TextView
    private lateinit var secondary: TextView
    private lateinit var error: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = RejoinUi.column(this).apply { setBackgroundColor(RejoinUi.canvas) }
        val bar = RejoinUi.column(this).apply {
            setPadding(RejoinUi.dp(context, 12), 0, RejoinUi.dp(context, 16), 0)
        }
        RejoinUi.header(this, bar, "Set up Rejoin")
        RejoinUi.add(page, bar)
        val body = RejoinUi.scrollBody(page)
        stepCaption = RejoinUi.label(this, "", 14f, RejoinUi.accent, true)
        RejoinUi.add(body, stepCaption)
        progress = RejoinUi.row(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        RejoinUi.add(body, progress, 8)
        heading = RejoinUi.label(this, "", 32f, bold = true).apply { RejoinUi.heading(this) }
        RejoinUi.add(body, heading, 24)
        detail = RejoinUi.label(this, "", 16f, RejoinUi.muted)
        RejoinUi.add(body, detail, 4)
        content = RejoinUi.column(this)
        RejoinUi.add(body, content, 20)
        error = RejoinUi.label(this, "", 14f, RejoinUi.rose).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        RejoinUi.add(body, error, 8)

        val footer = RejoinUi.footer(page)
        primary = RejoinUi.action(this, "Continue") { }
        secondary = RejoinUi.smallAction(this, "Not now") { deferSetup() }.apply { gravity = Gravity.CENTER }
        RejoinUi.add(footer, primary)
        RejoinUi.add(footer, secondary, 4)
        setContentView(page)
        RejoinUi.applySystemBars(this, page)
        render()
    }

    private fun step() = SetupFlow.next(TrustedNetworks.all(this).isNotEmpty(),
        CredentialVault.hasCredentials(this), prefs.getBoolean("http_opt_in", false))

    private fun render() {
        content.removeAllViews()
        progress.removeAllViews()
        error.visibility = View.GONE
        val step = step()
        stepCaption.text = "Step ${step.ordinal + 1} of ${SetupFlow.Step.entries.size}"
        SetupFlow.Step.entries.forEachIndexed { index, _ ->
            val bar = View(this).apply {
                background = RejoinUi.surface(context,
                    if (index <= step.ordinal) RejoinUi.accent else RejoinUi.line, 2)
            }
            progress.addView(bar, LinearLayout.LayoutParams(0, RejoinUi.dp(this, 3), 1f).apply {
                if (index > 0) marginStart = RejoinUi.dp(this@SetupActivity, 6)
            })
        }
        secondary.visibility = View.VISIBLE
        secondary.text = "Not now"
        secondary.setOnClickListener { deferSetup() }
        when (step) {
            SetupFlow.Step.WIFI -> showWifi()
            SetupFlow.Step.LOGIN -> showLogin()
            SetupFlow.Step.READY -> showReady()
        }
    }

    private fun showWifi() {
        heading.text = "Save your campus Wi-Fi"
        val permitted = RejoinUi.hasWifiAccess(this)
        val locationOn = WifiProbe.locationEnabled(this)
        val wifi = WifiProbe.current(this)
        when {
            !permitted -> {
                detail.text = "Android needs precise Location permission to show Rejoin which Wi-Fi you’re using."
                action("Allow Wi-Fi access") { RejoinUi.requestWifiAccess(this, WIFI_PERMISSION) }
            }
            !locationOn -> {
                detail.text = "Turn on Location so Rejoin can identify your Wi-Fi."
                action("Open Location settings") { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            }
            wifi == null -> {
                detail.text = "Join your campus Wi-Fi, then return here to save it."
                action("Open Wi-Fi settings") { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            }
            else -> {
                detail.text = "Rejoin will use your campus login only on Wi-Fi you save."
                val network = RejoinUi.inset(this)
                RejoinUi.add(network, RejoinUi.label(this, "Connected Wi-Fi", 14f, RejoinUi.muted))
                RejoinUi.add(network, RejoinUi.label(this, wifi.ssid, 20f, bold = true), 4)
                RejoinUi.add(content, network)
                action("Save this Wi-Fi") {
                    val current = WifiProbe.current(this)
                    if (current != wifi) {
                        render()
                        showError("Your Wi-Fi changed. Check the network before saving.")
                    } else if (TrustedNetworks.saveCurrent(this, wifi) == null) {
                        showError("Couldn’t save this Wi-Fi. Try again.")
                    } else {
                        ActivityHistory.record(this, "Trusted Wi-Fi saved")
                        render()
                    }
                }
            }
        }
    }

    private fun showLogin() {
        val alreadySaved = CredentialVault.hasCredentials(this)
        heading.text = if (alreadySaved) "Allow campus sign-in" else "Save your campus login"
        detail.text = if (alreadySaved) "Your login is saved on this phone. Review how Rejoin can use it."
            else "Use the username and password you enter on your campus Wi-Fi sign-in page."
        val form = LoginForm(this, consentOnly = alreadySaved)
        RejoinUi.add(content, form.view)
        action(if (alreadySaved) "Save permission" else "Save login") {
            if (form.save()) {
                getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(primary.windowToken, 0)
                ActivityHistory.record(this, if (alreadySaved) "Campus sign-in allowed" else "Campus login saved")
                render()
            }
        }
    }

    private fun showReady() {
        val canSignIn = TrustedNetworks.all(this).any { CredentialVault.hasLoginFor(this, it) }
        if (!canSignIn) {
            heading.text = "Choose a saved login"
            detail.text = "Assign a campus login to your saved Wi-Fi before turning on auto-reconnect."
            action("Manage saved Wi-Fi") { startActivity(Intent(this, NetworksActivity::class.java)) }
            return
        }
        val enabled = RecoveryPrefs.enabled(this)
        heading.text = if (enabled) "Auto-reconnect is on" else "Reconnect automatically?"
        detail.text = if (enabled) "Rejoin can sign you in when your campus Wi-Fi asks again."
            else "Turn it on to let Rejoin sign you in again when your campus session expires."
        val summary = RejoinUi.inset(this)
        RejoinUi.add(summary, RejoinUi.label(this, "Wi-Fi saved", 16f, RejoinUi.success, true))
        RejoinUi.add(summary, RejoinUi.label(this, "Login saved on this phone", 16f, RejoinUi.success, true), 8)
        RejoinUi.add(content, summary)
        secondary.visibility = if (enabled) View.GONE else View.VISIBLE
        secondary.text = "Keep it off"
        secondary.setOnClickListener { finishSetup() }
        action(if (enabled) "Done" else "Turn on auto-reconnect") {
            if (enabled || RecoveryPrefs.enable(this)) finishSetup()
            else showError("Android couldn’t start background checks. Try again or keep auto-reconnect off.")
        }
    }

    private fun action(text: String, onTap: () -> Unit) {
        primary.text = text
        primary.setOnClickListener { onTap() }
    }

    private fun showError(message: String) {
        error.text = message
        error.visibility = View.VISIBLE
    }

    private fun finishSetup() {
        prefs.edit().putBoolean("onboarding_completed", true)
            .putBoolean("onboarding_deferred", false).apply()
        finish()
    }

    private fun deferSetup() {
        prefs.edit().putBoolean("onboarding_deferred", true).apply()
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (step() != SetupFlow.Step.LOGIN) render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WIFI_PERMISSION) {
            render()
            if (!RejoinUi.hasWifiAccess(this)) RejoinUi.explainWifiAccess(this)
        }
    }

    companion object {
        private const val WIFI_PERMISSION = 71
    }
}
