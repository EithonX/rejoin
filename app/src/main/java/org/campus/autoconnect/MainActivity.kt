package org.campus.autoconnect

import android.Manifest
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
        text("v0.1 — Read-only diagnostics. No university credentials or automatic login.", 14f)
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
        button("Refresh status") { refresh() }
        text("This prototype never signs into the campus portal. Background checks are best effort and may be delayed by Android.", 13f)
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
