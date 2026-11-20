package com.eithonx.rejoin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

class NetworkDetailsActivity : RejoinActivity() {
    private val id by lazy { intent.getStringExtra(EXTRA_ID) }
    private var revision by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        if (TrustedNetworks.all(this).none { it.id == id }) finish()
        revision++
    }

    @Composable
    override fun Screen() {
        val profile = remember(revision) { TrustedNetworks.all(this).firstOrNull { it.id == id } } ?: return
        val accounts = remember(revision) { CredentialVault.list(this) }
        val defaultId = remember(revision) { CredentialVault.defaultId(this) }
        val login = accounts.firstOrNull { it.id == (profile.loginId ?: defaultId) }
        Page("Wi-Fi details") {
            ScreenIntro(profile.label, profile.ssid.takeIf { it != profile.label })
            TextButton(onClick = { rename(profile) }) { Text("Rename") }
            SectionLabel("Login")
            ListGroup {
                ActionRow(login?.label ?: "No login selected",
                    when {
                        profile.loginId == null && login != null -> "Follows default"
                        profile.loginId != null && login == null -> "Assigned login unavailable"
                        else -> login?.username
                    }, R.drawable.ic_key) {
                    if (accounts.isEmpty()) open(LoginEditorActivity::class.java)
                    else chooseLogin(profile, accounts, defaultId)
                }
            }
            TextButton(onClick = { open(LoginsActivity::class.java) }) { Text("Manage saved logins") }
            TextButton(onClick = { forget(profile) }) {
                Text("Forget this Wi-Fi", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    private fun rename(profile: TrustedNetworks.Profile) {
        showDialog { dismiss ->
            var label by remember { mutableStateOf(profile.label) }
            var failed by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("Rename Wi-Fi") },
                text = {
                    OutlinedTextField(value = label, onValueChange = { label = it.take(48); failed = false },
                        label = { Text("Name") }, singleLine = true, isError = failed,
                        supportingText = if (failed) ({ Text("Couldn’t save this name.") }) else null)
                },
                dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
                confirmButton = {
                    TextButton(enabled = label.isNotBlank(), onClick = {
                        if (TrustedNetworks.rename(this, profile.id, label)) {
                            dismiss()
                            revision++
                        } else failed = true
                    }) { Text("Save") }
                }
            )
        }
    }

    private fun chooseLogin(
        profile: TrustedNetworks.Profile,
        accounts: List<CredentialVault.SavedLogin>,
        defaultId: String?
    ) {
        showDialog { dismiss ->
            var assignment by remember { mutableStateOf(profile.loginId) }
            var failed by remember { mutableStateOf(false) }
            val selectedId = assignment ?: defaultId
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("Login for this Wi-Fi") },
                text = {
                    Column(
                        Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (defaultId != null) {
                            val followsDefault = assignment == null
                            ListItem(
                                modifier = Modifier.toggleable(followsDefault, role = Role.Switch) {
                                    assignment = if (it) null else defaultId
                                    failed = false
                                },
                                headlineContent = { Text("Follow default login") },
                                supportingContent = { Text("Switches when you change your default.") },
                                trailingContent = { Switch(checked = followsDefault, onCheckedChange = null) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            HorizontalDivider()
                        }
                        Column(Modifier.selectableGroup()) {
                            accounts.forEachIndexed { index, account ->
                                if (index > 0) HorizontalDivider()
                                SavedLoginRow(account, account.id == defaultId, account.id == selectedId) {
                                    if (account.id != selectedId) {
                                        assignment = account.id
                                        failed = false
                                    }
                                }
                            }
                        }
                        if (failed) Text("Couldn’t save this choice.", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
                confirmButton = {
                    TextButton(enabled = assignment != profile.loginId && accounts.any { it.id == selectedId },
                        onClick = {
                            if (TrustedNetworks.assignLogin(this, profile.id, assignment)) {
                                ActivityHistory.record(this, "Wi-Fi login updated")
                                revision++
                                dismiss()
                            } else failed = true
                        }
                    ) { Text("Save") }
                }
            )
        }
    }

    private fun forget(profile: TrustedNetworks.Profile) {
        confirm("Forget ${profile.label}?", "Rejoin will stop using your saved login on this network.",
            "Forget Wi-Fi", "Cancel") {
            if (TrustedNetworks.remove(this, profile.id)) {
                if (TrustedNetworks.all(this).isEmpty()) {
                    getSharedPreferences("profile", MODE_PRIVATE).edit()
                        .putBoolean("auto_enabled", false).commit()
                    RecoveryScheduler.disable(this)
                }
                ActivityHistory.record(this, "Wi-Fi forgotten")
                finish()
            } else notice("Couldn’t forget this Wi-Fi", "Try again.")
        }
    }

    companion object { const val EXTRA_ID = "profile_id" }
}
