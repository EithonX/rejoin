package com.eithonx.rejoin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
                        profile.loginId == null && login != null -> "Uses default login"
                        profile.loginId != null && login == null -> "Assigned login unavailable"
                        else -> null
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

    private fun chooseLogin(profile: TrustedNetworks.Profile, accounts: List<CredentialVault.SavedLogin>,
                            defaultId: String?) {
        val default = accounts.find { it.id == defaultId }
        fun assign(loginId: String?) {
            if (TrustedNetworks.assignLogin(this, profile.id, loginId)) {
                ActivityHistory.record(this, "Wi-Fi login updated")
                revision++
            } else notice("Couldn’t change login", "Try again.")
        }
        showDialog { dismiss ->
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("Choose a login") },
                text = {
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        ActionRow("Use default", default?.label) { dismiss(); assign(null) }
                        accounts.forEach { account ->
                            ActionRow(account.label, "Selected".takeIf { account.id == profile.loginId }) {
                                dismiss()
                                assign(account.id)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
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
