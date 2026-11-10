package com.eithonx.rejoin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

class SettingsActivity : RejoinActivity() {
    private var settings by mutableStateOf(SettingsUi())

    override fun onResume() {
        super.onResume()
        val auto = BackgroundHealth.inspect(this)
        settings = SettingsUi(
            networks = TrustedNetworks.all(this).size,
            logins = CredentialVault.list(this).size,
            automation = when {
                !auto.enabled -> "Off"
                auto.issue?.required == true -> "Needs attention"
                else -> "On"
            }
        )
    }

    @Composable
    override fun Screen() {
        SettingsScreen(settings, Appearance.current.label, ::finish,
            onNetworks = { open(NetworksActivity::class.java) },
            onLogins = { open(LoginsActivity::class.java) },
            onAutomation = { open(ProtectionActivity::class.java) },
            onAppearance = ::chooseAppearance,
            onHelp = { open(DiagnosticsActivity::class.java) },
            onAbout = { open(AboutActivity::class.java) })
    }

    private fun chooseAppearance() {
        showDialog { dismiss ->
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("Theme") },
                text = {
                    Column(Modifier.selectableGroup()) {
                        AppAppearance.entries.forEach { option ->
                            val selected = Appearance.current == option
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                selected = selected, role = Role.RadioButton, onClick = {
                                    Appearance.set(this@SettingsActivity, option)
                                    dismiss()
                                }), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RadioButton(selected, onClick = null)
                                Text(option.label)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
            )
        }
    }
}

internal data class SettingsUi(val networks: Int = 0, val logins: Int = 0, val automation: String = "Off")

@Composable
internal fun SettingsScreen(
    ui: SettingsUi,
    appearance: String,
    onBack: () -> Unit,
    onNetworks: () -> Unit,
    onLogins: () -> Unit,
    onAutomation: () -> Unit,
    onAppearance: () -> Unit,
    onHelp: () -> Unit,
    onAbout: () -> Unit
) {
    RejoinPage("Settings", onBack) {
        SectionLabel("Wi-Fi & sign-in")
        ListGroup {
            ActionRow("Saved Wi-Fi", when (ui.networks) {
                0 -> "No networks saved"
                1 -> "1 network saved"
                else -> "${ui.networks} networks saved"
            }, R.drawable.ic_wifi, onNetworks)
            HorizontalDivider()
            ActionRow("Saved logins", when (ui.logins) {
                0 -> "No logins saved"
                1 -> "1 login saved"
                else -> "${ui.logins} logins saved"
            }, R.drawable.ic_key, onLogins)
            HorizontalDivider()
            ActionRow("Auto sign-in", ui.automation, R.drawable.ic_shield, onAutomation)
        }

        SectionLabel("Appearance")
        ListGroup { ActionRow("Theme", appearance, R.drawable.ic_theme, onAppearance) }

        SectionLabel("Support")
        ListGroup {
            ActionRow("Troubleshooting", icon = R.drawable.ic_activity, onClick = onHelp)
            HorizontalDivider()
            ActionRow("About", "Version and updates", R.drawable.ic_about, onAbout)
        }
    }
}
