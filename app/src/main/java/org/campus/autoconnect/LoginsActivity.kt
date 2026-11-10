package org.campus.autoconnect

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** A default saved login is enough; extra logins are optional. */
class LoginsActivity : Activity() {
    private lateinit var listing: LinearLayout
    private lateinit var headline: TextView
    private lateinit var addLoginButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this).apply { setBackgroundColor(RejoinUi.canvas) }
        val body = RejoinUi.column(this).apply {
            val d = RejoinUi.dp(this@LoginsActivity, 24)
            setPadding(d, d, d, RejoinUi.dp(this@LoginsActivity, 32))
        }
        root.addView(body)
        setContentView(root)
        RejoinUi.applySystemBars(this, root)

        RejoinUi.header(this, body, "Saved logins")
        headline = RejoinUi.label(this, "No password saved", 15f, RejoinUi.muted)
        RejoinUi.add(body, headline, 17)

        addLoginButton = RejoinUi.action(this, "Save your login") { showEditor(null) }
        RejoinUi.add(body, addLoginButton, 26)

        RejoinUi.section(body, "On this phone", 32)
        listing = RejoinUi.column(this)
        RejoinUi.add(body, listing, 8)

        RejoinUi.add(body, RejoinUi.label(this,
            "Campus sign-in uses HTTP, so passwords aren't encrypted in transit.",
            12f, RejoinUi.amber), 25)
        refresh()
    }

    private fun refresh() {
        if (!::listing.isInitialized) return
        listing.removeAllViews()
        val accounts = CredentialVault.list(this)
        addLoginButton.text = if (accounts.isEmpty()) "Save your login" else "Add another login"
        headline.text = when (accounts.size) {
            0 -> "No password saved"
            1 -> "✓  Password saved on this device"
            else -> "✓  " + accounts.size + " logins saved securely"
        }
        if (accounts.isEmpty()) {
            RejoinUi.add(listing, RejoinUi.label(this,
                "Save one login to use on your trusted Wi-Fi.", 14f, RejoinUi.muted), 18)
            return
        }
        val default = CredentialVault.defaultId(this)
        for ((i, account) in accounts.withIndex()) {
            if (i > 0) RejoinUi.add(listing, RejoinUi.rule(this), 15)
            val top = RejoinUi.row(this)
            val details = RejoinUi.column(this)
            top.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            RejoinUi.add(details, RejoinUi.label(this, account.label, 16f, RejoinUi.ink, true))
            RejoinUi.add(details, RejoinUi.label(this,
                if (account.id == default) "DEFAULT LOGIN" else "ADDITIONAL LOGIN",
                10f, RejoinUi.jade, true), 7)
            top.addView(RejoinUi.smallAction(this, "Edit  ›") {
                showEditor(account)
            })
            RejoinUi.add(listing, top, 19)
            RejoinUi.add(listing, RejoinUi.label(this,
                "✓  Password saved · " + account.username, 12f, RejoinUi.muted), 7)
            val buttons = RejoinUi.row(this)
            if (account.id != default) {
                val makeDefault = RejoinUi.smallAction(this, "Make default") {
                    if (CredentialVault.setDefault(this, account.id)) {
                        ActivityHistory.record(this, "Default login updated")
                        refresh()
                    }
                }
                buttons.addView(makeDefault, LinearLayout.LayoutParams(0, -2, 1f))
            } else {
                buttons.addView(RejoinUi.label(this, "Used by default",
                    12f, RejoinUi.muted), LinearLayout.LayoutParams(0, -2, 1f))
            }
            buttons.addView(RejoinUi.smallAction(this, "Remove") { confirmRemove(account) })
            RejoinUi.add(listing, buttons, 8)
        }
    }

    private fun showEditor(existing: CredentialVault.SavedLogin?) {
        val form = RejoinUi.column(this).apply {
            val side = RejoinUi.dp(this@LoginsActivity, 22)
            setPadding(side, RejoinUi.dp(this@LoginsActivity, 8), side, 0)
        }
        val name = EditText(this).apply {
            hint = "Name (e.g. My campus login)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            setText(existing?.label ?: if (CredentialVault.list(this@LoginsActivity).isEmpty())
                "Campus login" else "")
        }
        val uid = EditText(this).apply {
            hint = "University username"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(existing?.username.orEmpty())
        }
        val password = EditText(this).apply {
            hint = if (existing == null) "Password" else "New password (leave empty to keep saved)"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val consent = CheckBox(this).apply {
            text = "I understand that this campus portal uses unencrypted HTTP for sign-in."
            textSize = 13f
            isChecked = getSharedPreferences("profile", MODE_PRIVATE)
                .getBoolean("http_opt_in", false)
        }
        form.addView(name)
        form.addView(uid)
        form.addView(password)
        form.addView(consent)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Save login" else "Edit saved login")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (name.text.isBlank() || uid.text.isBlank() ||
                    (existing == null && password.text.isBlank()) || !consent.isChecked) {
                    AlertDialog.Builder(this).setTitle("Finish setting up your login")
                        .setMessage("Add a name, username, password and acknowledge the HTTP warning.")
                        .setPositiveButton("OK", null).show()
                    return@setOnClickListener
                }
                val saved = CredentialVault.upsert(this, existing?.id,
                    name.text.toString(), uid.text.toString(), password.text.toString())
                if (!saved) {
                    AlertDialog.Builder(this).setTitle("Couldn't save login")
                        .setMessage("Check the fields or the saved-login limit.")
                        .setPositiveButton("OK", null).show()
                    return@setOnClickListener
                }
                getSharedPreferences("profile", MODE_PRIVATE)
                    .edit().putBoolean("http_opt_in", true).commit()
                password.text.clear()
                ActivityHistory.record(this, "Saved login updated")
                dialog.dismiss()
                refresh()
            }
        }
        dialog.show()
    }

    private fun confirmRemove(account: CredentialVault.SavedLogin) {
        val linked = TrustedNetworks.all(this).count { it.loginId == account.id }
        val detail = if (linked > 0)
            "This login is assigned to " + linked + " saved Wi-Fi " +
                (if (linked == 1) "network" else "networks") +
                ". Those networks will revert to your default login."
        else "This removes its saved password from this device."
        AlertDialog.Builder(this).setTitle("Remove " + account.label + "?")
            .setMessage(detail)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                if (CredentialVault.remove(this, account.id)) {
                    if (!CredentialVault.hasCredentials(this)) {
                        getSharedPreferences("profile", MODE_PRIVATE)
                            .edit().putBoolean("auto_enabled", false).commit()
                        RecoveryScheduler.disable(this)
                    }
                    ActivityHistory.record(this, "Saved login removed")
                    refresh()
                }
            }.show()
    }

    override fun onResume() {
        super.onResume()
        if (::listing.isInitialized) refresh()
    }
}
