package com.eithonx.rejoin

import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class LoginsActivity : RejoinActivity() {
    private var revision by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        revision++
    }

    @Composable
    override fun Screen() {
        val accounts = remember(revision) { CredentialVault.list(this) }
        val defaultId = remember(revision) { CredentialVault.defaultId(this) }
        Page("Saved logins") {
            if (accounts.isEmpty()) {
                ScreenIntro("Add your first login", "Use the login from your Wi-Fi sign-in page.")
            }
            PageButton("Add login") { openEditor() }
            if (accounts.isNotEmpty()) ListGroup {
                accounts.forEachIndexed { index, account ->
                    if (index > 0) HorizontalDivider()
                    val isDefault = account.id == defaultId
                    ActionRow(account.label, if (isDefault) "Default login" else "Password saved", R.drawable.ic_key) {
                        manageLogin(account, isDefault)
                    }
                }
            }
        }
    }

    private fun openEditor(id: String? = null) {
        startActivity(Intent(this, LoginEditorActivity::class.java).putExtra(LoginEditorActivity.EXTRA_ID, id))
    }

    private fun manageLogin(account: CredentialVault.SavedLogin, isDefault: Boolean) {
        showDialog { dismiss ->
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(account.label) },
                text = {
                    ListGroup {
                        ActionRow("Edit login") { dismiss(); openEditor(account.id) }
                        if (!isDefault) ActionRow("Make default") {
                            dismiss()
                            if (CredentialVault.setDefault(this, account.id)) {
                                ActivityHistory.record(this, "Default login updated")
                                revision++
                            } else notice("Couldn’t change the default login", "Try again.")
                        }
                        ActionRow("Remove login") { dismiss(); confirmRemove(account) }
                    }
                },
                confirmButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
            )
        }
    }

    private fun confirmRemove(account: CredentialVault.SavedLogin) {
        val linked = TrustedNetworks.all(this).any { it.loginId == account.id }
        val detail = if (linked) "Networks using this login will return to your default login, if available."
            else "The saved password will be removed from this device."
        confirm("Remove ${account.label}?", detail, "Remove", "Cancel") {
            if (CredentialVault.remove(this, account.id)) {
                if (!CredentialVault.hasCredentials(this)) {
                    getSharedPreferences("profile", MODE_PRIVATE).edit()
                        .putBoolean("auto_enabled", false).commit()
                    RecoveryScheduler.disable(this)
                }
                ActivityHistory.record(this, "Saved login removed")
                revision++
            } else notice("Couldn’t remove this login", "Try again.")
        }
    }
}
