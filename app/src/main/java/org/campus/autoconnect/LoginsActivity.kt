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
        headline.text = when (accounts.size) {
            0 -> "No login saved"
            1 -> "✓  Password saved on this device"
            else -> "✓  " + accounts.size + " logins saved on this device"
        }
        addLoginButton.text = if (accounts.isEmpty()) "Save your login" else "Add login"
        if (accounts.isEmpty()) {
            RejoinUi.add(listing, RejoinUi.label(this,
                "Your saved login will be used for trusted Wi-Fi.", 13f, RejoinUi.muted), 19)
            return
        }
        val defaultId = CredentialVault.defaultId(this)
        accounts.forEachIndexed { index, account ->
            if (index > 0) RejoinUi.add(listing, RejoinUi.rule(this), 3)
            val detail = when {
                account.id == defaultId -> "Default login · Password saved"
                else -> "Password saved"
            }
            listing.addView(RejoinUi.navRow(this, account.label, detail) {
                manageLogin(account, account.id == defaultId)
            })
        }
    }

    private fun manageLogin(account: CredentialVault.SavedLogin, isDefault: Boolean) {
        val choices = if (isDefault) arrayOf("Edit login", "Remove login")
            else arrayOf("Edit login", "Make default", "Remove login")
        AlertDialog.Builder(this).setTitle(account.label)
            .setItems(choices) { _, which ->
                when {
                    which == 0 -> showEditor(account)
                    isDefault && which == 1 -> confirmRemove(account)
                    !isDefault && which == 1 -> {
                        if (CredentialVault.setDefault(this, account.id)) {
                            ActivityHistory.record(this, "Default login updated")
                            refresh()
                        }
                    }
                    else -> confirmRemove(account)
                }
            }
            .setNegativeButton("Cancel", null).show()
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
