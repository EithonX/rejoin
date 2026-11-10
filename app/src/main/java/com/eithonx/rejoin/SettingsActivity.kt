package com.eithonx.rejoin

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SettingsActivity : RejoinActivity() {
    private var settings by mutableStateOf(SettingsUi())

    override fun onResume() {
        super.onResume()
        val auto = BackgroundHealth.inspect(this)
        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        settings = SettingsUi(
            networks = TrustedNetworks.all(this).size,
            logins = CredentialVault.list(this).size,
            automation = when (auto.level) {
                BackgroundHealth.Level.PAUSED -> "Off"
                BackgroundHealth.Level.NEEDS_ACTION -> "Review background access"
                BackgroundHealth.Level.READY -> "On"
            },
            version = version
        )
    }

    @Composable
    override fun Screen() {
        SettingsScreen(settings, ::finish,
            onNetworks = { open(NetworksActivity::class.java) },
            onLogins = { open(LoginsActivity::class.java) },
            onAutomation = { open(ProtectionActivity::class.java) },
            onHelp = { open(DiagnosticsActivity::class.java) },
            onDeveloper = { openLink("https://github.com/EithonX", "Developer") })
    }
}

internal data class SettingsUi(
    val networks: Int = 0,
    val logins: Int = 0,
    val automation: String = "Off",
    val version: String = ""
)

@Composable
internal fun SettingsScreen(
    ui: SettingsUi,
    onBack: () -> Unit,
    onNetworks: () -> Unit,
    onLogins: () -> Unit,
    onAutomation: () -> Unit,
    onHelp: () -> Unit,
    onDeveloper: () -> Unit
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

        SectionLabel("Support")
        ListGroup {
            ActionRow("Troubleshooting", icon = R.drawable.ic_activity, onClick = onHelp)
            HorizontalDivider()
            ActionRow("Developer", "EithonX on GitHub", R.drawable.ic_external, onDeveloper)
        }
        if (ui.version.isNotBlank()) BodyText("Rejoin ${ui.version}")
    }
}
