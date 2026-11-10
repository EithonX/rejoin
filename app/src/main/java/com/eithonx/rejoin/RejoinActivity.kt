package com.eithonx.rejoin

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

abstract class RejoinActivity : ComponentActivity() {
    private var dialog by mutableStateOf<(@Composable () -> Unit)?>(null)

    private val wifiPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onWifiPermissionResult()
        if (!hasWifiAccess()) explainWifiAccess()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RejoinTheme {
                Screen()
                dialog?.invoke()
            }
        }
    }

    @Composable
    protected abstract fun Screen()

    @Composable
    protected fun Page(title: String, content: @Composable () -> Unit) {
        RejoinPage(title, onBack = ::finish, content = content)
    }

    protected fun open(activity: Class<out ComponentActivity>) {
        startActivity(Intent(this, activity))
    }

    protected fun notice(title: String, detail: String) {
        confirm(title, detail, "OK")
    }

    protected fun confirm(
        title: String,
        detail: String,
        action: String,
        cancel: String? = null,
        onConfirm: () -> Unit = {}
    ) {
        showDialog { dismiss ->
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(title) },
                text = { Text(detail) },
                confirmButton = { TextButton(onClick = { dismiss(); onConfirm() }) { Text(action) } },
                dismissButton = cancel?.let { label ->
                    { TextButton(onClick = dismiss) { Text(label) } }
                }
            )
        }
    }

    protected fun showDialog(content: @Composable (() -> Unit) -> Unit) {
        dialog = { content { dialog = null } }
    }

    protected fun hasWifiAccess() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    protected fun requestWifiAccess() {
        wifiPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    protected open fun onWifiPermissionResult() = Unit

    protected fun explainWifiAccess() {
        confirm("Allow precise location",
            "Android requires precise location permission to identify your Wi-Fi. In app settings, go to Permissions → Location.",
            "Open app settings", "Cancel", ::openAppSettings)
    }

    protected fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)))
    }

    protected fun openSignInPage() {
        if (!LiveSessionTest.selectedWifiMatches(this)) {
            notice("Connect to saved Wi-Fi", "Join a saved network before opening its sign-in page.")
            return
        }
        openLink("http://172.16.2.1:1000/login?", "Sign-in page")
    }

    protected fun openLink(url: String, title: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            notice(title, "No browser is available to open this link.")
        }
    }
}
