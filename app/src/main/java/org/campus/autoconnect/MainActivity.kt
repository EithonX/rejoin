package org.campus.autoconnect

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var info: TextView
    private lateinit var profile: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 24)
        }
        setContentView(ScrollView(this).apply { addView(layout) })
        fun text(content: String, size: Float = 16f): TextView {
            return TextView(this).apply {
                this.text = content
                textSize = size
                setTextColor(Color.rgb(28, 37, 47))
                setPadding(0, 12, 0, 12)
                layout.addView(this)
            }
        }
        fun button(title: String, onClick: () -> Unit) {
            layout.addView(Button(this).apply {
                text = title
                isAllCaps = false
                setOnClickListener { onClick() }
            })
        }
        text("Campus AutoConnect Lab", 24f)
        text("v0.4 — Controlled live session test. No stored credentials or automatic login.", 14f)
        profile = text("No trusted Wi-Fi")
        info = text("Status: Not tested")
        button("1. Grant Wi-Fi identity permission") {
            if (!allowed()) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 10)
            else info.text = "Permission already granted"
        }
        button("2. Trust current Wi-Fi") {
            if (!allowed()) {
                info.text = "Grant Wi-Fi identity permission first"
            } else {
                val current = WifiProbe.current(this)
                if (current == null) info.text = "Can't identify Wi-Fi. Enable location services."
                else {
                    WifiProbe.trust(this, current)
                    info.text = "Selected Wi-Fi saved. No request sent."
                    refresh()
                }
            }
        }
        button("3. Check selected Wi-Fi now") {
            info.text = "Checking selected Wi-Fi..."
            Thread {
                val result = WifiProbe.probe(applicationContext)
                runOnUiThread { info.text = "${result.status}; HTTP ${result.code ?: "none"}" }
            }.start()
        }
        button("Toggle 30-minute background checks") {
            if (ProbeJobService.enabled(this)) ProbeJobService.cancel(this)
            else if (allowed() && WifiProbe.current(this) != null) ProbeJobService.schedule(this)
            else info.text = "Grant permission and trust a Wi-Fi first"
            refresh()
        }
        button("4. Run instant offline expiry simulation") {
            val result = SessionSimulation.run()
            info.text = result
        }
        button("5. Read CU login form (native socket)") {
            info.text = "Inspecting official campus portal on the selected Wi-Fi..."
            Thread {
                val result = PortalInspector.inspect(applicationContext)
                runOnUiThread { info.text = result }
            }.start()
        }
        button("6. Live test: log out CU shared session") {
            AlertDialog.Builder(this)
                .setTitle("Log out the shared CU connection?")
                .setMessage("This can disconnect every device using your router until someone signs in again. This test only runs once when you confirm.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out") { _, _ ->
                    info.text = "Requesting university logout over the selected Wi-Fi..."
                    Thread {
                        val result = LiveSessionTest.logout(applicationContext)
                        runOnUiThread { info.text = result }
                    }.start()
                }.show()
        }
        button("7. Open CU sign-in page") {
            if (!LiveSessionTest.selectedWifiMatches(applicationContext)) {
                info.text = "Connect to your trusted Wi-Fi first. No browser opened."
            } else {
                info.text = "Opening official university login page in the system browser. This app never sees your password."
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://172.16.2.1:1000/login?")))
                } catch (_: Exception) {
                    info.text = "No browser available; open the CU Wi-Fi sign-in notification instead."
                }
            }
        }
        button("8. Verify internet restored") {
            info.text = "Checking the selected Wi-Fi only..."
            Thread {
                val r = WifiProbe.probe(applicationContext)
                runOnUiThread {
                    info.text = "Verification: " + r.status + "; HTTP " + (r.code?.toString() ?: "unavailable") +
                        "\nOnline (204) indicates internet access. A portal redirect suggests sign-in is still required."
                }
            }.start()
        }
        button("Refresh status") { refresh() }
        text("Real logout requires confirmation. Sign-in happens only in your browser, not this app. CU uses an HTTP login page, so credential transport is not encrypted on that endpoint. No automatic login or password storage. Private DNS is unchanged.", 13f)
        refresh()
    }

    private fun allowed() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10) refresh()
    }
    private fun refresh() {
        profile.text = WifiProbe.describe(this)
        val p = getSharedPreferences("profile", MODE_PRIVATE)
        val last = if (p.contains("last_checked")) {
            "\nLast check: " + DateFormat.getDateTimeInstance().format(Date(p.getLong("last_checked", 0))) +
                "\n" + p.getString("last_state", "")
        } else ""
        info.text = "Background checks: " + (if (ProbeJobService.enabled(this)) "enabled" else "disabled") + last
    }
}
