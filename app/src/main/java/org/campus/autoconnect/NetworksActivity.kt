package org.campus.autoconnect

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Saved-router management is a destination, not a stack of home-screen cards. */
class NetworksActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var count: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this).apply { setBackgroundColor(RejoinUi.canvas) }
        val body = RejoinUi.column(this).apply {
            setPadding(RejoinUi.dp(this@NetworksActivity, 24),
                RejoinUi.dp(this@NetworksActivity, 24),
                RejoinUi.dp(this@NetworksActivity, 24),
                RejoinUi.dp(this@NetworksActivity, 32))
        }
        root.addView(body)
        setContentView(root)
        RejoinUi.applySystemBars(this, root)
        RejoinUi.header(this, body, "Trusted Wi-Fi")
        count = RejoinUi.label(this, "", 13f, RejoinUi.muted)
        RejoinUi.add(body, count, 18)

        RejoinUi.section(body, "Saved networks", 29)
        val group = RejoinUi.inset(this)
        RejoinUi.add(body, group, 12)
        list = RejoinUi.column(this)
        RejoinUi.add(group, list)

        val add = RejoinUi.action(this, "Save connected Wi-Fi") { saveNetwork() }
        RejoinUi.add(body, add, 25)
        RejoinUi.add(body, RejoinUi.label(this,
            "Rejoin only signs in on Wi-Fi you've saved. A different router with the same name must be added separately.",
            12f, RejoinUi.muted), 14)
        refresh()
    }

    private fun refresh() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val profiles = TrustedNetworks.all(this)
        count.text = when (profiles.size) {
            0 -> "Nothing saved yet."
            1 -> "1 network saved"
            else -> profiles.size.toString() + " networks saved"
        }
        if (profiles.isEmpty()) {
            RejoinUi.add(list, RejoinUi.label(this,
                "Connect to your campus Wi-Fi, then save it here.",
                14f, RejoinUi.muted), 7, 7)
        }
        val active = WifiProbe.current(this)?.let { TrustedNetworks.find(this, it) }
        profiles.forEachIndexed { i, profile ->
            if (i > 0) RejoinUi.add(list, RejoinUi.rule(this), 4)
            val assigned = CredentialVault.list(this).firstOrNull { it.id == profile.loginId }
            val detail = when {
                active?.id == profile.id -> "Currently connected"
                assigned != null -> "Uses " + assigned.label
                profile.loginId != null -> "Assigned login missing"
                else -> "Uses default login"
            }
            val item = RejoinUi.navRow(this, profile.label, detail) {
                startActivity(Intent(this, NetworkDetailsActivity::class.java)
                    .putExtra(NetworkDetailsActivity.EXTRA_ID, profile.id))
            }
            list.addView(item)
        }
    }

    private fun saveNetwork() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 31)
            return
        }
        val wifi = WifiProbe.current(this)
        if (wifi == null) {
            AlertDialog.Builder(this).setTitle("Wi-Fi not detected")
                .setMessage("Connect to the Wi-Fi you want to save and enable Location access.")
                .setPositiveButton("OK", null).show()
            return
        }
        val existing = TrustedNetworks.find(this, wifi)
        if (existing != null) {
            startActivity(Intent(this, NetworkDetailsActivity::class.java)
                .putExtra(NetworkDetailsActivity.EXTRA_ID, existing.id))
            return
        }
        AlertDialog.Builder(this).setTitle("Save " + wifi.ssid + "?")
            .setMessage("Rejoin will only use your saved account on this specific Wi-Fi access point.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save Wi-Fi") { _, _ ->
                val saved = TrustedNetworks.saveCurrent(this, wifi)
                if (saved == null) AlertDialog.Builder(this)
                    .setMessage("This Wi-Fi couldn't be saved. Check the network limit.")
                    .setPositiveButton("OK", null).show()
                else {
                    ActivityHistory.record(this, "Trusted network saved")
                    refresh()
                }
            }.show()
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
        permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 31 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            saveNetwork()
    }
}
