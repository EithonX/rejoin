package org.campus.autoconnect

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Exact router identity, friendly label and optional saved-login assignment. */
class NetworkDetailsActivity : Activity() {
    companion object { const val EXTRA_ID = "profile_id" }

    private var id: String = ""
    private lateinit var label: TextView
    private lateinit var login: TextView
    private lateinit var identity: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        id = intent.getStringExtra(EXTRA_ID).orEmpty()
        if (id.isBlank() || find() == null) {
            finish()
            return
        }
        val root = RejoinUi.column(this).apply { setBackgroundColor(RejoinUi.canvas) }
        val scroll = ScrollView(this)
        val body = RejoinUi.column(this).apply {
            val side = RejoinUi.dp(this@NetworkDetailsActivity, 23)
            setPadding(side, RejoinUi.dp(this@NetworkDetailsActivity, 25), side, side)
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        setContentView(root)
        RejoinUi.applySystemBars(this, root)

        val header = RejoinUi.row(this)
        header.addView(RejoinUi.smallAction(this, "←") { finish() },
            LinearLayout.LayoutParams(RejoinUi.dp(this, 38), -2))
        header.addView(RejoinUi.label(this, "Trusted Wi-Fi", 27f, RejoinUi.ink, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        RejoinUi.add(body, header)

        val main = RejoinUi.inset(this)
        RejoinUi.add(body, main, 24)
        label = RejoinUi.label(this, "", 20f, RejoinUi.ink, true)
        RejoinUi.add(main, label)
        identity = RejoinUi.label(this, "", 12f, RejoinUi.muted)
        RejoinUi.add(main, identity, 10)
        RejoinUi.add(main, RejoinUi.action(this, "Rename this Wi-Fi", false) { rename() }, 17)

        val accountPanel = RejoinUi.inset(this)
        RejoinUi.add(body, accountPanel, 15)
        RejoinUi.add(accountPanel, RejoinUi.label(this, "LOGIN FOR THIS WI-FI",
            11f, RejoinUi.muted, true).apply { letterSpacing = .13f })
        login = RejoinUi.label(this, "", 17f, RejoinUi.ink, true)
        RejoinUi.add(accountPanel, login, 14)
        RejoinUi.add(accountPanel, RejoinUi.label(this,
            "Using your default login is recommended. You only need another login if this router requires a different account.",
            12f, RejoinUi.muted), 8)
        RejoinUi.add(accountPanel, RejoinUi.action(this, "Choose saved login", false) {
            chooseLogin()
        }, 15)
        RejoinUi.add(accountPanel, RejoinUi.smallAction(this, "Manage saved logins  →") {
            startActivity(Intent(this, LoginsActivity::class.java))
        }, 10)

        RejoinUi.add(body, RejoinUi.smallAction(this, "Remove this trusted Wi-Fi") {
            confirmRemove()
        }, 19)
        RejoinUi.add(body, RejoinUi.label(this,
            "Rejoin recognizes the exact Wi-Fi access point you saved. If your router changes its identity, save the new connection.",
            12f, RejoinUi.muted), 13)
        refresh()
    }

    private fun find(): TrustedNetworks.Profile? =
        TrustedNetworks.all(this).firstOrNull { it.id == id }

    private fun refresh() {
        val item = find() ?: run { finish(); return }
        label.text = item.label
        identity.text = item.ssid + "\n" + item.bssid
        val accounts = CredentialVault.list(this)
        val selected = if (item.loginId == null) {
            accounts.firstOrNull { it.id == CredentialVault.defaultId(this) }
        } else accounts.firstOrNull { it.id == item.loginId }
        login.text = when {
            selected != null -> "✓  " + selected.label + " · Password saved"
            item.loginId != null -> "Assigned login missing"
            else -> "Default login not saved yet"
        }
    }

    private fun rename() {
        val current = find() ?: return
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(current.label)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            selectAll()
        }
        val layout = RejoinUi.column(this).apply {
            val p = RejoinUi.dp(this@NetworkDetailsActivity, 20)
            setPadding(p, 0, p, 0)
            addView(input)
        }
        AlertDialog.Builder(this).setTitle("Name this Wi-Fi")
            .setView(layout)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                if (TrustedNetworks.rename(this, id, input.text.toString())) refresh()
            }.show()
    }

    private fun chooseLogin() {
        val profile = find() ?: return
        val accounts = CredentialVault.list(this)
        val default = accounts.firstOrNull { it.id == CredentialVault.defaultId(this) }
        val choices = arrayOf("Use default" + if (default == null) " (not saved yet)" else " · " + default.label) +
            accounts.map { it.label }.toTypedArray()
        val checked = if (profile.loginId == null) 0 else
            accounts.indexOfFirst { it.id == profile.loginId }.let { if (it < 0) 0 else it + 1 }
        AlertDialog.Builder(this).setTitle("Login for this Wi-Fi")
            .setSingleChoiceItems(choices, checked) { dialog, which ->
                val accountId = if (which == 0) null else accounts.getOrNull(which - 1)?.id
                if (TrustedNetworks.assignLogin(this, profile.id, accountId)) {
                    ActivityHistory.record(this, "Wi-Fi login preference saved")
                    refresh()
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmRemove() {
        val profile = find() ?: return
        AlertDialog.Builder(this).setTitle("Forget " + profile.label + "?")
            .setMessage("Rejoin will no longer automatically log in on this Wi-Fi. Your saved account passwords won't be deleted.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Forget Wi-Fi") { _, _ ->
                if (TrustedNetworks.remove(this, id)) {
                    if (TrustedNetworks.all(this).isEmpty()) {
                        getSharedPreferences("profile", MODE_PRIVATE)
                            .edit().putBoolean("auto_enabled", false).commit()
                        RecoveryScheduler.disable(this)
                    }
                    ActivityHistory.record(this, "Trusted Wi-Fi removed")
                    finish()
                }
            }.show()
    }

    override fun onResume() {
        super.onResume()
        if (::label.isInitialized) refresh()
    }
}
