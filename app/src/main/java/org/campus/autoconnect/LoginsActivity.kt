package org.campus.autoconnect

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.widget.LinearLayout

class LoginsActivity : Activity() {
    private lateinit var body: LinearLayout

    override fun onResume() {
        super.onResume()
        body = RejoinUi.screen(this, "Saved logins")
        refresh()
    }

    private fun refresh() {
        body.removeAllViews()
        val accounts = CredentialVault.list(this)
        if (accounts.isEmpty()) {
            RejoinUi.add(body, RejoinUi.label(this, "Save your campus login", 25f, bold = true))
            RejoinUi.add(body, RejoinUi.label(this,
                "Use the login you already enter on your campus Wi-Fi sign-in page.",
                15f, RejoinUi.muted), 6)
        } else {
            val defaultId = CredentialVault.defaultId(this)
            accounts.forEachIndexed { index, account ->
                if (index > 0) RejoinUi.add(body, RejoinUi.rule(this))
                val detail = if (account.id == defaultId) "Default login · Password saved" else "Password saved"
                RejoinUi.add(body, RejoinUi.navRow(this, account.label, detail, R.drawable.ic_key) {
                    manageLogin(account, account.id == defaultId)
                })
            }
        }
        RejoinUi.add(body, RejoinUi.action(this, "Add login") { openEditor() }, 16)
    }

    private fun openEditor(id: String? = null) {
        startActivity(Intent(this, LoginEditorActivity::class.java)
            .putExtra(LoginEditorActivity.EXTRA_ID, id))
    }

    private fun manageLogin(account: CredentialVault.SavedLogin, isDefault: Boolean) {
        val choices = if (isDefault) arrayOf("Edit login", "Remove login")
            else arrayOf("Edit login", "Make default", "Remove login")
        AlertDialog.Builder(this).setTitle(account.label)
            .setItems(choices) { _, choice ->
                when {
                    choice == 0 -> openEditor(account.id)
                    !isDefault && choice == 1 -> {
                        if (CredentialVault.setDefault(this, account.id)) {
                            ActivityHistory.record(this, "Default login updated")
                            refresh()
                        } else showError("Couldn’t change the default login.")
                    }
                    else -> confirmRemove(account)
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmRemove(account: CredentialVault.SavedLogin) {
        val linked = TrustedNetworks.all(this).count { it.loginId == account.id }
        val detail = if (linked > 0)
            "Wi-Fi using this login will return to your default login, if you have one."
        else "The password for this login will be removed from this phone."
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
                } else showError("Couldn’t remove this login.")
            }.show().also { RejoinUi.destructive(it) }
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show()
    }
}
