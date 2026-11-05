package org.campus.autoconnect

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Configuration is intentional and separate from the one-glance dashboard. */
class SettingsActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var networkLabel: TextView
    private lateinit var credentialsLabel: TextView
    private lateinit var modes: LinearLayout
    private lateinit var advanced: LinearLayout
    private lateinit var advancedToggle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val outer = RejoinUi.column(this).apply { setBackgroundColor(RejoinUi.canvas) }
        val scroller = ScrollView(this).apply {
            clipToPadding = false
            setBackgroundColor(RejoinUi.canvas)
        }
        val body = RejoinUi.column(this).apply {
            setPadding(RejoinUi.dp(this@SettingsActivity, 23), RejoinUi.dp(this@SettingsActivity, 24),
                RejoinUi.dp(this@SettingsActivity, 23), RejoinUi.dp(this@SettingsActivity, 30))
        }
        scroller.addView(body)
        outer.addView(scroller, LinearLayout.LayoutParams(-1, -1))
        setContentView(outer)
        RejoinUi.applySystemBars(this, outer)

        val titleRow = RejoinUi.row(this)
        titleRow.addView(RejoinUi.smallAction(this, "←", { finish() }),
            LinearLayout.LayoutParams(RejoinUi.dp(this, 38), -2))
        titleRow.addView(RejoinUi.label(this, "Settings", 27f, RejoinUi.ink, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        RejoinUi.add(body, titleRow)
        RejoinUi.add(body, RejoinUi.label(this, "Make Rejoin work the way you do.",
            13f, RejoinUi.muted), 6)

        section(body, "YOUR NETWORK", 28)
        val networkCard = RejoinUi.inset(this)
        RejoinUi.add(body, networkCard, 11)
        networkLabel = RejoinUi.label(this, "No trusted Wi-Fi yet", 16f, RejoinUi.ink, true)
        RejoinUi.add(networkCard, networkLabel)
        RejoinUi.add(networkCard, RejoinUi.label(this,
            "Only this Wi-Fi can trigger saved campus authentication.",
            12f, RejoinUi.muted), 8)
        RejoinUi.add(networkCard, RejoinUi.action(this, "Use currently connected Wi-Fi", false) {
            selectNetwork()
        }, 17)
        RejoinUi.add(networkCard, RejoinUi.smallAction(this, "Location & background access  →") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + packageName)))
        }, 10)
        RejoinUi.add(networkCard, RejoinUi.label(this,
            "Android may require background location access to identify Wi-Fi while the phone is locked.",
            11f, RejoinUi.muted), 8)

        section(body, "AUTOMATION", 25)
        val modeCard = RejoinUi.inset(this)
        RejoinUi.add(body, modeCard, 11)
        RejoinUi.add(modeCard, RejoinUi.label(this, "Recovery pace", 17f, RejoinUi.ink, true))
        RejoinUi.add(modeCard, RejoinUi.label(this,
            "No constant background service. Both modes respond to network events when Android delivers them.",
            12f, RejoinUi.muted), 8)
        modes = RejoinUi.row(this)
        RejoinUi.add(modeCard, modes, 15)
        RejoinUi.add(modeCard, RejoinUi.label(this,
            "Balanced checks about every 30 minutes when healthy. Responsive uses a 15-minute backup. Android may defer either.",
            12f, RejoinUi.muted), 12)
        rebuildModes()

        section(body, "YOUR ACCOUNT", 25)
        val accountCard = RejoinUi.inset(this)
        RejoinUi.add(body, accountCard, 11)
        credentialsLabel = RejoinUi.label(this, "No login saved", 16f, RejoinUi.ink, true)
        RejoinUi.add(accountCard, credentialsLabel)
        RejoinUi.add(accountCard, RejoinUi.label(this,
            "Stored using Android Keystore encryption and never uploaded to a Rejoin server.",
            12f, RejoinUi.muted), 7)
        RejoinUi.add(accountCard, RejoinUi.action(this, "Update CU login", false) {
            showCredentialsDialog()
        }, 16)
        RejoinUi.add(accountCard, RejoinUi.label(this,
            "Transport notice: CU's observed login endpoint uses plain HTTP. Passwords are not encrypted in transit. Auto-login remains opt-in.",
            12f, RejoinUi.amber), 14)

        section(body, "MORE", 25)
        val advancedCard = RejoinUi.inset(this)
        RejoinUi.add(body, advancedCard, 11)
        advancedToggle = RejoinUi.smallAction(this, "Advanced & testing   +") {
            advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            advancedToggle.text = if (advanced.visibility == View.VISIBLE)
                "Advanced & testing   −" else "Advanced & testing   +"
        }
        RejoinUi.add(advancedCard, advancedToggle)
        advanced = RejoinUi.column(this).apply { visibility = View.GONE }
        RejoinUi.add(advancedCard, advanced, 8)
        RejoinUi.add(advanced, RejoinUi.label(this,
            "These tools aren't needed for ordinary use. Logging out affects every device sharing the router.",
            12f, RejoinUi.muted), 7)
        RejoinUi.add(advanced, RejoinUi.action(this, "Sign out shared CU session", false) {
            confirmLogout()
        }, 15)
        RejoinUi.add(advanced, RejoinUi.action(this, "Sign in once (test)", false) {
            confirmManualLogin()
        }, 9)
        RejoinUi.add(advanced, RejoinUi.action(this, "Check Wi-Fi internet", false) {
            runCheck()
        }, 9)
        RejoinUi.add(advanced, RejoinUi.action(this, "Open CU browser fallback", false) {
            if (LiveSessionTest.selectedWifiMatches(this)) startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("http://172.16.2.1:1000/login?"))
            )
            else showResult("Trusted network", "Connect to your selected Wi-Fi first.")
        }, 9)
        RejoinUi.add(advanced, RejoinUi.action(this, "Erase saved account", false) {
            confirmErase()
        }, 9)

        val footer = RejoinUi.label(this, "REJOIN  ·  PRIVATE BETA  ·  v2.0", 10f,
            RejoinUi.muted, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.10f
        }
        RejoinUi.add(body, footer, 30)
        refresh()
    }

    private fun section(root: LinearLayout, title: String, top: Int) {
        RejoinUi.add(root, RejoinUi.label(this, title, 11f, RejoinUi.muted, true)
            .apply { letterSpacing = 0.14f }, top)
    }

    private fun rebuildModes() {
        modes.removeAllViews()
        val current = RecoveryPrefs.mode(this)
        for ((index, mode) in listOf(RecoveryPolicy.Mode.BALANCED,
            RecoveryPolicy.Mode.RESPONSIVE).withIndex()) {
            val selected = current == mode
            val title = if (mode == RecoveryPolicy.Mode.BALANCED) "Balanced" else "Responsive"
            val button = RejoinUi.action(this, title, selected) {
                val p = getSharedPreferences("profile", MODE_PRIVATE)
                p.edit().putString("recovery_mode", mode.name.lowercase()).commit()
                if (RecoveryPrefs.enabled(this))
                    RecoveryScheduler.schedule(this, 60_000L)
                ActivityHistory.record(this, title + " recovery selected")
                rebuildModes()
            }
            modes.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index == 0) rightMargin = RejoinUi.dp(this@SettingsActivity, 5)
                else leftMargin = RejoinUi.dp(this@SettingsActivity, 5)
            })
        }
    }

    private fun selectNetwork() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 52)
            return
        }
        val wifi = WifiProbe.current(this)
        if (wifi == null) {
            showResult("Wi-Fi identity unavailable",
                "Connect to your router and turn on Location, then try again.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Trust this Wi-Fi?")
            .setMessage(wifi.ssid + "\n" + wifi.bssid +
                "\n\nOnly this access point can use saved login credentials.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Trust") { _, _ ->
                WifiProbe.trust(this, wifi)
                ActivityHistory.record(this, "Trusted Wi-Fi selected")
                refresh()
                if (RecoveryPrefs.enabled(this)) RecoveryScheduler.initialize(this)
            }.show()
    }

    private fun showCredentialsDialog() {
        val parent = RejoinUi.column(this).apply {
            setPadding(RejoinUi.dp(this@SettingsActivity, 22), RejoinUi.dp(this@SettingsActivity, 8),
                RejoinUi.dp(this@SettingsActivity, 22), 0)
        }
        val savedUid = CredentialVault.read(this)?.username.orEmpty()
        val username = EditText(this).apply {
            hint = "University UID"
            setSingleLine(true)
            setText(savedUid)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val password = EditText(this).apply {
            hint = "Campus Wi-Fi password"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val consent = CheckBox(this).apply {
            text = "I authorize Rejoin to authenticate this account through CU's HTTP portal, knowing that HTTP does not encrypt passwords in transit."
            textSize = 13f
            isChecked = getSharedPreferences("profile", MODE_PRIVATE)
                .getBoolean("http_opt_in", false)
        }
        parent.addView(username)
        parent.addView(password)
        parent.addView(consent)
        val dialog = AlertDialog.Builder(this)
            .setTitle("CU account")
            .setView(parent)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!consent.isChecked || username.text.isBlank() || password.text.isBlank()) {
                    showResult("Incomplete setup",
                        "Enter your UID and password, then acknowledge the HTTP security notice.")
                    return@setOnClickListener
                }
                if (!CredentialVault.save(this, username.text.toString(), password.text.toString())) {
                    showResult("Couldn't save credentials", "Check both fields and try again.")
                    return@setOnClickListener
                }
                getSharedPreferences("profile", MODE_PRIVATE).edit()
                    .putBoolean("http_opt_in", true).apply()
                password.text.clear()
                ActivityHistory.record(this, "Campus login updated")
                dialog.dismiss()
                refresh()
            }
        }
        dialog.show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Sign out everyone on this router?")
            .setMessage("The shared CU session may close for all devices. This is a test, not routine recovery.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Sign out") { _, _ ->
                getSharedPreferences("profile", MODE_PRIVATE).edit()
                    .putLong("manual_test_until", System.currentTimeMillis() + 10 * 60_000L)
                    .apply()
                execute("logout")
            }.show()
    }

    private fun confirmManualLogin() {
        AlertDialog.Builder(this)
            .setTitle("Try one campus sign-in?")
            .setMessage("Uses your saved CU account. The gateway's HTTP traffic is not encrypted.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Sign in") { _, _ -> execute("login") }
            .show()
    }

    private fun runCheck() = execute("check")

    private fun confirmErase() {
        AlertDialog.Builder(this)
            .setTitle("Erase your stored account?")
            .setMessage("Automatic recovery will turn off, and this phone's saved credentials will be deleted.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Erase") { _, _ ->
                RecoveryScheduler.disable(this)
                CredentialVault.clear(this)
                ActivityHistory.record(this, "Stored account erased")
                refresh()
            }.show()
    }

    private fun execute(action: String) {
        executor.execute {
            val result = try {
                when (action) {
                    "logout" -> LiveSessionTest.logout(applicationContext)
                    "login" -> SessionEngine.run(applicationContext,
                        userInitiated = true, explicitlyLoggedOut = true)
                    else -> {
                        val r = InternetVerifier.check(applicationContext)
                        r.state.name + ": " + r.details
                    }
                }
            } catch (e: Exception) { "Test unavailable (" + e.javaClass.simpleName + ")" }
            runOnUiThread {
                ActivityHistory.record(this, when (action) {
                    "logout" -> "Manual logout requested"
                    "login" -> "Manual login tested"
                    else -> "Internet checked"
                })
                showResult("Connection test", result)
            }
        }
    }

    private fun showResult(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title).setMessage(message)
            .setPositiveButton("OK", null).show()
    }

    private fun refresh() {
        if (!::networkLabel.isInitialized) return
        val p = getSharedPreferences("profile", MODE_PRIVATE)
        val ssid = p.getString("ssid", null)
        networkLabel.text = if (ssid == null) "No trusted network yet"
            else if (LiveSessionTest.selectedWifiMatches(this)) ssid + " · connected"
            else ssid + " · currently away"
        credentialsLabel.text = if (CredentialVault.hasCredentials(this))
            "Credentials secured on this phone" else "No credentials saved"
    }

    override fun onResume() {
        super.onResume()
        if (::networkLabel.isInitialized) refresh()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 52 && results.isNotEmpty() &&
            results[0] == PackageManager.PERMISSION_GRANTED) selectNetwork()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
