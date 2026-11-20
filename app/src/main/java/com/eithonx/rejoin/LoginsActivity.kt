package com.eithonx.rejoin

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

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
            if (accounts.isNotEmpty()) ListGroup {
                accounts.forEachIndexed { index, account ->
                    if (index > 0) HorizontalDivider()
                    val isDefault = account.id == defaultId
                    SavedLoginRow(account, isDefault) {
                        manageLogin(account, isDefault)
                    }
                }
            }
            PageButton("Add login", filled = accounts.isEmpty()) { openEditor() }
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

@Composable
internal fun SavedLoginRow(
    account: CredentialVault.SavedLogin,
    isDefault: Boolean,
    selected: Boolean? = null,
    onClick: () -> Unit
) {
    ListItem(
        modifier = if (selected == null) Modifier.clickable(role = Role.Button, onClick = onClick)
            else Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick),
        headlineContent = { Text(account.label) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(account.username)
                if (isDefault) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text("Default", Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        leadingContent = if (selected == null) ({ Icon(painterResource(R.drawable.ic_key), null) }) else null,
        trailingContent = {
            if (selected == null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            else RadioButton(selected = selected, onClick = null)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
