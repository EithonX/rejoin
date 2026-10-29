package org.campus.autoconnect

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

/**
 * Functional v0.5 UI. A future release will replace this with a polished dashboard.
 * No external analytics or servers; secrets never leave this device except when the
 * user explicitly authorizes their own normal CU authentication.
 */
class MainActivity : Activity() {
    private lateinit var info: TextView
    private lateinit var profile: TextView
    private lateinit var uid: EditText
    private lateinit var pwd: EditText
    private lateinit var risk: CheckBox
    private lateinit var scheduler: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 24)
        }
        setContentView(ScrollView(this).apply { addView(root) })
        fun label(message: String, size: Float = 16f): TextView {
            return TextView(this).apply {
                text = message
                textSize = size
                setTextColor(Color.rgb(30, 40, 52))
                setPadding(0, 10, 0, 10)
                root.addView(this)
            }
        }
        fun button(title: String, onTap: () -> Unit) {
            root.addView(Button(this).apply {
                text = title
                isAllCaps = false
                setOnClickListener { onTap() }
            })
        }
        label("Campus AutoConnect Lab", 24f)
        label("v0.7 — Direct login recovery • stage diagnostics", 14f)
        profile = label("No selected network", 15f)
        scheduler = label("Automatic repair: disabled", 15f)
        info = label("Set up a trusted network first.")

        button("1. Trust currently connected Wi-Fi") {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 23)
            } else {
                val network = WifiProbe.current(this)
                if (network == null) info.text = "Wi-Fi identity unavailable; enable Location in Android settings."
                else {
                    WifiProbe.trust(this, network)
                    info.text = "Trusted network saved. Run initial check while internet works to cache probe address."
                    refreshLabels()
                }
            }
        }
        button("2. Initial Wi-Fi connectivity check") { runCheck(false) }

        label("Your CU credentials (stored encrypted on this phone)", 15f)
        uid = EditText(this).apply {
            hint = "University UID"
            inputType = InputType.TYPE_CLASS_TEXT
            root.addView(this)
        }
        pwd = EditText(this).apply {
            hint = "University internet password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            root.addView(this)
        }
        risk = CheckBox(this).apply {
            text = "I understand CU's observed login uses unencrypted HTTP. I authorize this app to submit my own credentials to that CU portal on my selected Wi-Fi."
            textSize = 14f
            root.addView(this)
        }
        button("3. Save credentials & HTTP consent") {
            if (!risk.isChecked) {
                info.text = "Consent required: the campus HTTP portal does not protect passwords in transit."
            } else if (!LiveSessionTest.selectedWifiMatches(this)) {
                info.text = "Connect to and select the trusted Wi-Fi before saving."
            } else {
                val ok = CredentialVault.save(this, uid.text.toString(), pwd.text.toString())
                if (ok) {
                    getSharedPreferences("profile", MODE_PRIVATE).edit()
                        .putBoolean("http_opt_in", true).commit()
                    pwd.text.clear()
                    info.text = "Credentials encrypted and saved locally. Auto repair is still OFF until enabled."
                } else info.text = "Credential storage failed; check the fields."
                refreshLabels()
            }
        }
        button("4. Toggle automatic background repair") {
            val p = getSharedPreferences("profile", MODE_PRIVATE)
            if (p.getBoolean("auto_enabled", false)) {
                p.edit().putBoolean("auto_enabled", false).commit()
                ProbeJobService.cancel(this)
                info.text = "Automatic repair disabled."
            } else {
                if (!CredentialVault.hasCredentials(this) || !p.getBoolean("http_opt_in", false)) {
                    info.text = "Save credentials and accept the HTTP transport warning first."
                } else if (!LiveSessionTest.selectedWifiMatches(this)) {
                    info.text = "Connect to the trusted Wi-Fi first."
                } else {
                    val scheduled = ProbeJobService.schedule(this)
                    p.edit().putBoolean("auto_enabled", scheduled).commit()
                    info.text = if (scheduled) "Automatic repair enabled; Android schedules checks approximately every 15 minutes." else "Background scheduling failed."
                }
            }
            refreshLabels()
        }
        button("5. Check and repair connection now") { runCheck(true) }

        button("6. Test real logout + automatic recovery") {
            if (!CredentialVault.hasCredentials(this) ||
                !getSharedPreferences("profile", MODE_PRIVATE).getBoolean("auto_enabled", false)) {
                info.text = "Enable automatic repair before attempting the live recovery test."
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Temporarily disconnect the shared connection?")
                    .setMessage("CU logout can interrupt everyone using your router. After a confirmed logout, the app will try one login, verify connectivity, and stop. It will not keep retrying.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Log out and test") { _, _ ->
                        info.text = "Testing shared logout and recovery..."
                        Thread {
                            val logout = LiveSessionTest.logout(applicationContext)
                            Thread.sleep(1600)
                            val succeeded = logout.contains("Server: HTTP/1.1") || logout.contains("Server: HTTP/1.0")
                            val recovery = try {
                                if (succeeded) {
                                    SessionEngine.run(applicationContext, userInitiated = true, explicitlyLoggedOut = true) { stage ->
                                        runOnUiThread { info.text = "Live test\n" + stage }
                                    }
                                } else {
                                    "Logout was not confirmed; automatic recovery not forced."
                                }
                            } catch (e: Exception) {
                                "Recovery stopped with " + e.javaClass.simpleName
                            }
                            runOnUiThread { info.text = logout + "\n\nRecovery: " + recovery; refreshLabels() }
                        }.start()
                    }.show()
            }
        }
        button("Open CU official login page (manual fallback)") {
            if (!LiveSessionTest.selectedWifiMatches(this)) {
                info.text = "Connect to the trusted Wi-Fi first."
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://172.16.2.1:1000/login?")))
            }
        }
        button("Open Android app settings / background location access") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + packageName)))
        }
        button("Disable automatic login and erase credentials") {
            AlertDialog.Builder(this).setTitle("Erase stored credentials?")
                .setMessage("Stops automatic repair and deletes the device's encrypted credential record.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Erase") { _, _ ->
                    ProbeJobService.cancel(this)
                    CredentialVault.clear(this)
                    uid.text.clear()
                    pwd.text.clear()
                    info.text = "Credentials erased. Automatic repair stopped."
                    refreshLabels()
                }.show()
        }
        button("Refresh status") { refreshLabels() }
        label("Overnight testing: keep Wi-Fi connected and allow this app background location access if Android offers 'Allow all the time'. Battery optimisation and Doze can delay checks; no continuous foreground service runs. The app never sends credentials to third-party servers.", 13f)
        refreshLabels()
    }

    private fun runCheck(repair: Boolean) {
        info.text = if (repair) "Checking Wi-Fi and recovering if needed..." else "Checking selected Wi-Fi..."
        Thread {
            val result = if (repair) SessionEngine.run(applicationContext, userInitiated = true)
                else {
                    val probe = WifiProbe.probe(applicationContext, allowDns = true)
                    val outcome = probe.status + "; HTTP " + (probe.code?.toString() ?: "none")
                    getSharedPreferences("profile", MODE_PRIVATE).edit()
                        .putString("last_result", outcome)
                        .putLong("last_checked", System.currentTimeMillis()).apply()
                    outcome
                }
            runOnUiThread { info.text = result; refreshLabels() }
        }.start()
    }

    private fun refreshLabels() {
        profile.text = WifiProbe.describe(this)
        val p = getSharedPreferences("profile", MODE_PRIVATE)
        val enabled = p.getBoolean("auto_enabled", false)
        val lastTime = p.getLong("last_checked", 0L)
        val stamp = if (lastTime > 0L) DateFormat.getDateTimeInstance().format(Date(lastTime)) else "Never"
        scheduler.text = "Automatic repair: " + (if (enabled) "ON" else "OFF") +
            "\nScheduled job: " + (if (ProbeJobService.enabled(this)) "present" else "not scheduled") +
            "\nEncrypted credentials: " + (if (CredentialVault.hasCredentials(this)) "saved" else "not saved") +
            "\nLast check: " + stamp +
            "\nLast result: " + p.getString("last_result", "No checks yet")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 23) {
            info.text = if (results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED)
                "Permission granted; tap 'Trust currently connected Wi-Fi'." else "Permission denied."
        }
    }
}
