package org.campus.autoconnect

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Three real tasks, no introductory slides:
 *  1. Trust the Wi-Fi currently connected
 *  2. Save the single default login
 *  3. Enable automation
 *
 * Progress is derived from saved state and survives leaving the app.
 */
class SetupActivity : Activity() {
    private lateinit var root: ScrollView
    private lateinit var body: LinearLayout
    private lateinit var stepCaption: TextView
    private lateinit var header: TextView
    private lateinit var helper: TextView
    private lateinit var workArea: LinearLayout
    private lateinit var primary: TextView
    private lateinit var secondary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(RejoinUi.canvas)
        }
        body = RejoinUi.column(this).apply {
            val side = RejoinUi.dp(this@SetupActivity, 27)
            setPadding(side, RejoinUi.dp(this@SetupActivity, 30), side, side)
        }
        root.addView(body)
        setContentView(root)
        RejoinUi.applySystemBars(this, root)

        val top = RejoinUi.row(this)
        top.addView(RejoinUi.icon(this, R.drawable.ic_rejoin_mark, 39),
            LinearLayout.LayoutParams(RejoinUi.dp(this, 39), RejoinUi.dp(this, 39)))
        top.addView(RejoinUi.label(this, "rejoin", 24f, RejoinUi.ink, true),
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                leftMargin = RejoinUi.dp(this@SetupActivity, 11)
            })
        top.addView(RejoinUi.smallAction(this, "Not now") { deferSetup() })
        RejoinUi.add(body, top)
        stepCaption = RejoinUi.label(this, "", 11f, RejoinUi.jade, true)
            .apply { letterSpacing = .13f }
        RejoinUi.add(body, stepCaption, 55)

        header = RejoinUi.label(this, "", 31f, RejoinUi.ink, true)
        RejoinUi.add(body, header, 17)
        helper = RejoinUi.label(this, "", 14f, RejoinUi.muted).apply {
            setLineSpacing(RejoinUi.dp(this@SetupActivity, 4).toFloat(), 1f)
        }
        RejoinUi.add(body, helper, 12)

        workArea = RejoinUi.column(this)
        RejoinUi.add(body, workArea, 37)

        primary = RejoinUi.action(this, "Continue") {}
        RejoinUi.add(body, primary, 25)
        secondary = RejoinUi.smallAction(this, "Not now") { deferSetup() }
            .apply { gravity = Gravity.CENTER }
        RejoinUi.add(body, secondary, 10)

        render()
    }

    private fun stage(): Int {
        if (TrustedNetworks.all(this).isEmpty()) return 1
        if (!CredentialVault.hasCredentials(this) ||
            !getSharedPreferences("profile", MODE_PRIVATE).getBoolean("http_opt_in", false))
            return 2
        return 3
    }

    private fun render() {
        if (!::workArea.isInitialized) return
        workArea.removeAllViews()
        when (stage()) {
            1 -> showNetworkStage()
            2 -> showAccountStage()
            else -> showReadyStage()
        }
    }

    private fun showNetworkStage() {
        stepCaption.text = "STEP 1 OF 3"
        header.text = "Your Wi-Fi."
        helper.text = "Connect to the Wi-Fi you want Rejoin to remember."
        val snapshot = WifiProbe.current(this)
        val panel = RejoinUi.inset(this)
        RejoinUi.add(workArea, panel)
        RejoinUi.add(panel, RejoinUi.label(this, "CURRENT NETWORK", 11f,
            RejoinUi.muted, true).apply { letterSpacing = .1f })
        RejoinUi.add(panel, RejoinUi.label(this, snapshot?.ssid ?: "Not connected",
            19f, RejoinUi.ink, true), 13)
        RejoinUi.add(panel, RejoinUi.label(this,
            if (snapshot == null) "Connect to Wi-Fi first"
            else "We'll remember this router", 12f,
            if (snapshot == null) RejoinUi.amber else RejoinUi.muted), 8)

        primary.text = if (snapshot == null) "Open Wi-Fi settings" else "Save this Wi-Fi"
        primary.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 71)
                return@setOnClickListener
            }
            val wifi = WifiProbe.current(this)
            if (wifi == null) {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            } else {
                val result = TrustedNetworks.saveCurrent(this, wifi)
                if (result != null) {
                    ActivityHistory.record(this, "Trusted Wi-Fi saved")
                    render()
                } else alert("Couldn't save Wi-Fi", "Try again after checking your connection.")
            }
        }
        secondary.text = "I'll do this later"
        secondary.setOnClickListener { deferSetup() }
    }

    private fun showAccountStage() {
        stepCaption.text = "STEP 2 OF 3"
        header.text = "Save your login."
        helper.text = "Use the username and password from your campus sign-in page."

        val uid = EditText(this).apply {
            hint = "University username"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(CredentialVault.list(this@SetupActivity)
                .firstOrNull()?.username.orEmpty())
            setTextColor(RejoinUi.ink)
            setHintTextColor(RejoinUi.muted)
            setPadding(RejoinUi.dp(this@SetupActivity, 14),
                RejoinUi.dp(this@SetupActivity, 14),
                RejoinUi.dp(this@SetupActivity, 14),
                RejoinUi.dp(this@SetupActivity, 14))
            background = RejoinUi.surface(this@SetupActivity, RejoinUi.panel, 13, RejoinUi.line)
        }
        val password = EditText(this).apply {
            hint = "Password"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(RejoinUi.ink)
            setHintTextColor(RejoinUi.muted)
            setPadding(RejoinUi.dp(this@SetupActivity, 14),
                RejoinUi.dp(this@SetupActivity, 14),
                RejoinUi.dp(this@SetupActivity, 14),
                RejoinUi.dp(this@SetupActivity, 14))
            background = RejoinUi.surface(this@SetupActivity, RejoinUi.panel, 13, RejoinUi.line)
        }
        RejoinUi.add(workArea, uid)
        RejoinUi.add(workArea, password, 13)

        val disclosure = CheckBox(this).apply {
            text = "CU uses HTTP. I understand my password may be exposed in transit and allow Rejoin to sign in."
            textSize = 12f
            setTextColor(RejoinUi.muted)
            buttonTintList = android.content.res.ColorStateList.valueOf(RejoinUi.jade)
            isChecked = getSharedPreferences("profile", MODE_PRIVATE)
                .getBoolean("http_opt_in", false)
        }
        RejoinUi.add(workArea, disclosure, 23)

        primary.text = "Save login"
        primary.setOnClickListener {
            val name = uid.text.toString().trim()
            val secret = password.text.toString()
            if (name.isBlank() || secret.isBlank()) {
                alert("Add your login", "Enter both your university username and password.")
                return@setOnClickListener
            }
            if (!disclosure.isChecked) {
                alert("Permission needed",
                    "Review the campus HTTP warning and choose whether to allow sign-in.")
                return@setOnClickListener
            }
            val saved = CredentialVault.save(this, name, secret)
            if (!saved) {
                alert("Couldn't save login", "Your login wasn't saved. Try again.")
                return@setOnClickListener
            }
            getSharedPreferences("profile", MODE_PRIVATE)
                .edit().putBoolean("http_opt_in", true).commit()
            password.text.clear()
            ActivityHistory.record(this, "Campus login saved")
            render()
        }
        secondary.text = "I'll do this later"
        secondary.setOnClickListener { deferSetup() }
    }

    private fun showReadyStage() {
        stepCaption.text = "STEP 3 OF 3"
        header.text = "You're all set."
        helper.text = "Your Wi-Fi and password are saved. Rejoin can take it from here."

        val panel = RejoinUi.inset(this)
        RejoinUi.add(workArea, panel)
        val summary = listOf("✓  Wi-Fi saved", "✓  Login saved on this phone")
        summary.forEachIndexed { index, text ->
            if (index > 0) RejoinUi.add(panel, RejoinUi.rule(this), 15)
            RejoinUi.add(panel, RejoinUi.label(this, text, 15f, RejoinUi.ink, true),
                if (index == 0) 2 else 14)
        }

        primary.text = "Turn on auto-reconnect"
        primary.setOnClickListener {
            val prefs = getSharedPreferences("profile", MODE_PRIVATE)
            prefs.edit().putBoolean("auto_enabled", true).commit()
            val scheduled = RecoveryScheduler.schedule(this, 15_000L)
            if (!scheduled) {
                prefs.edit().putBoolean("auto_enabled", false).commit()
                alert("Couldn't enable auto-reconnect",
                    "You can try again from the home screen.")
                return@setOnClickListener
            }
            RecoveryScheduler.registerWifiEvents(this)
            ActivityHistory.record(this, "Auto-reconnect enabled")
            finishSetup()
        }
        secondary.text = "Use manual mode instead"
        secondary.setOnClickListener { finishSetup() }
    }

    private fun alert(title: String, detail: String) {
        AlertDialog.Builder(this).setTitle(title)
            .setMessage(detail).setPositiveButton("OK", null).show()
    }

    private fun finishSetup() {
        getSharedPreferences("profile", MODE_PRIVATE).edit()
            .putBoolean("onboarding_completed", true)
            .putBoolean("onboarding_deferred", false).apply()
        finish()
    }

    private fun deferSetup() {
        getSharedPreferences("profile", MODE_PRIVATE).edit()
            .putBoolean("onboarding_deferred", true).apply()
        finish()
    }

    override fun onResume() {
        super.onResume()
        // A user may come back from Wi-Fi settings with a new connection.
        if (::workArea.isInitialized && stage() == 1) render()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
        permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 71) {
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) render()
            else alert("Wi-Fi permission needed",
                "Allow Location access so Rejoin can identify the router you choose.")
        }
    }
}
