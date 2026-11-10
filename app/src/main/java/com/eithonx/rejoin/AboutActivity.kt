package com.eithonx.rejoin

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import java.io.File

class AboutActivity : RejoinActivity() {
    private var ready by mutableStateOf<File?>(null)
    private var installPermissionPending = false
    private val installPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val requested = installPermissionPending
            installPermissionPending = false
            if (requested && Build.VERSION.SDK_INT >= 26 && packageManager.canRequestPackageInstalls()) {
                ReleaseUpdates.available?.let(::download)
            }
        }

    override fun onResume() {
        super.onResume()
        ReleaseUpdates.refresh(this)
    }

    @Composable
    override fun Screen() {
        val update = ReleaseUpdates.available
        val check = ReleaseUpdates.check
        Page("About") {
            ListGroup {
                Row(Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(painterResource(R.drawable.ic_rejoin_mark), null, Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Rejoin", style = MaterialTheme.typography.titleLarge)
                        BodyText("Version ${BuildConfig.VERSION_NAME}")
                    }
                }
            }

            SectionLabel("Updates")
            when {
                ReleaseUpdates.downloading -> {
                    BodyText("Downloading and verifying the APK…")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                ready != null -> PageButton("Install verified update") {
                    ready?.takeIf(File::isFile)?.let(::install) ?: run { ready = null }
                }
                update != null -> {
                    ListGroup {
                        ActionRow("Version ${update.version} available", "Release notes", R.drawable.ic_external) {
                            openLink(update.notesUrl, "Release notes")
                        }
                    }
                    if (!BuildConfig.DEBUG) PageButton("Download update") { requestDownload(update) }
                    else BodyText("This debug build uses a different signing key. Get the signed APK from Releases.")
                }
            }
            if (!ReleaseUpdates.downloading) {
                val message = when (check) {
                    ReleaseUpdates.Check.IDLE, ReleaseUpdates.Check.AVAILABLE -> null
                    ReleaseUpdates.Check.CHECKING -> "Checking GitHub…"
                    ReleaseUpdates.Check.CURRENT -> "You’re up to date."
                    ReleaseUpdates.Check.INCOMPATIBLE -> "The latest release needs a newer Android version."
                    ReleaseUpdates.Check.NO_RELEASE -> "No public release is available yet."
                    ReleaseUpdates.Check.UNAVAILABLE -> "Couldn’t reach GitHub. Try again when you’re online."
                }
                message?.let { BodyText(it) }
                if (check != ReleaseUpdates.Check.CHECKING) {
                    PageButton("Check for updates", filled = false) { ReleaseUpdates.refresh(this, manual = true) }
                }
            }

            SectionLabel("Project")
            ListGroup {
                ActionRow("Developer", "EithonX", R.drawable.ic_external) {
                    openLink("https://github.com/EithonX", "Developer")
                }
                HorizontalDivider()
                ActionRow("Releases", icon = R.drawable.ic_external) {
                    openLink(RELEASES_PAGE, "Releases")
                }
            }
        }
    }

    private fun requestDownload(update: ReleaseUpdate) {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            confirm("Allow Rejoin to install updates",
                "Allow installs from Rejoin in Android settings. Android will ask you to confirm the update.",
                "Open settings", "Cancel") {
                try {
                    installPermissionPending = true
                    installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.fromParts("package", packageName, null)))
                } catch (_: ActivityNotFoundException) {
                    installPermissionPending = false
                    notice("Installation settings unavailable", "Open the release page to download the APK.")
                }
            }
        } else download(update)
    }

    private fun download(update: ReleaseUpdate) {
        ReleaseUpdates.download(this, update) { file, error ->
            if (isDestroyed || isFinishing) return@download
            if (file != null) {
                ready = file
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) install(file)
            } else when (error) {
                ReleaseUpdates.DownloadError.VERIFICATION ->
                    notice("Update couldn’t be verified", "The APK did not match this installation or release.")
                else -> notice("Download couldn’t finish", "Check your connection and try again.")
            }
        }
    }

    private fun install(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.updates", file)
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: ActivityNotFoundException) {
            notice("Installer unavailable", "Open the release page to install the APK manually.")
        }
    }
}
