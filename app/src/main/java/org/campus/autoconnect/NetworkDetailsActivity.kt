package org.campus.autoconnect

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
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
        val body = RejoinUi.screen(this, "Wi-Fi details")

        label = RejoinUi.label(this, "", 26f, RejoinUi.ink, true)
        RejoinUi.add(body, label)
        identity = RejoinUi.label(this, "", 13f, RejoinUi.muted)
        RejoinUi.add(body, identity, 8)
        RejoinUi.add(body, RejoinUi.smallAction(this, "Rename") { rename() }, 11)

        RejoinUi.section(body, "Login for this Wi-Fi", 31)
        login = RejoinUi.label(this, "", 15f, RejoinUi.ink, true)
        RejoinUi.add(body, login, 14)

        RejoinUi.add(body, RejoinUi.action(this, "Change login", false) {
            chooseLogin()
        }, 21)
        RejoinUi.add(body, RejoinUi.smallAction(this, "Saved logins") {
            startActivity(Intent(this, LoginsActivity::class.java))
        }, 11)

        RejoinUi.add(body, RejoinUi.rule(this), 29)
        val forget = RejoinUi.smallAction(this, "Forget this Wi-Fi") { confirmRemove() }
        RejoinUi.add(body, forget, 12)
        refresh()
    }

    private fun find(): TrustedNetworks.Profile? =
        TrustedNetworks.all(this).firstOrNull { it.id == id }

    private fun refresh() {
        val item = find() ?: run { finish(); return }
        label.text = item.label
        identity.text = if (item.label == item.ssid) "Saved Wi-Fi" else item.ssid + " · Saved"
        val accounts = CredentialVault.list(this)
        val selected = if (item.loginId == null) {
            accounts.firstOrNull { it.id == CredentialVault.defaultId(this) }
        } else accounts.firstOrNull { it.id == item.loginId }
        login.text = when {
            selected != null -> selected.label + if (item.loginId == null) " · Default login" else " · Saved"
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
            accounts.indexOfFirst { it.id == profile.loginId }.let { if (it < 0) -1 else it + 1 }
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
            .setMessage("Rejoin will no longer automatically log in on this Wi-Fi. You can save this Wi-Fi again from Settings.")
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
                } else AlertDialog.Builder(this).setMessage("Couldn’t forget this Wi-Fi.")
                    .setPositiveButton("OK", null).show()
            }.show().also { RejoinUi.destructive(it) }
    }

    override fun onResume() {
        super.onResume()
        if (::label.isInitialized) refresh()
    }
}
