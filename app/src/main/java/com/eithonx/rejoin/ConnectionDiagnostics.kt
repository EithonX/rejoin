package com.eithonx.rejoin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.text.DateFormat
import java.util.Date

internal object ConnectionDiagnostics {
    fun report(context: Context): String {
        val preferences = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val wifi = WifiProbe.current(context)
        val profile = wifi?.let { TrustedNetworks.find(context, it) }
        val account = profile?.let { CredentialVault.resolve(context, it) }
        val observation = if (wifi != null && profile != null)
            ConnectionObservation.read(preferences, profile.id, wifi) else null
        val background = BackgroundHealth.inspect(context)
        val active = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val wifiCapabilities = wifi?.let { connectivity.getNetworkCapabilities(it.network) }
        val precise = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val backgroundLocation = Build.VERSION.SDK_INT < 29 ||
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

        val history = preferences.getString("background_state", null)
            ?.takeIf { it in setOf("checking", "finished", "stopped by Android") } ?: "No recorded run"
        return buildString {
            appendLine("Rejoin ${BuildConfig.VERSION_NAME} · Android API ${Build.VERSION.SDK_INT}")
            appendLine("Default route: ${transport(active)}")
            appendLine("Wi-Fi identity available: ${yesNo(wifi != null)}")
            appendLine("Saved Wi-Fi match: ${yesNo(profile != null)}")
            appendLine("Android Wi-Fi status: ${wifiStatus(wifiCapabilities)}")
            appendLine("Precise location: ${yesNo(precise)}")
            appendLine("Location enabled: ${yesNo(WifiProbe.locationEnabled(context))}")
            appendLine("Background location: ${yesNo(backgroundLocation)}")
            appendLine("Saved login assigned: ${yesNo(account != null)}")
            appendLine("Unencrypted sign-in allowed: ${yesNo(preferences.getBoolean("http_opt_in", false))}")
            appendLine("Auto sign-in: ${if (background.enabled) "On" else "Off"}")
            background.issue?.let { appendLine("Background setup: ${it.title}") }
            appendLine("Background check: $history")
            appendLine("Last background check: ${date(background.lastRun)}")
            if (observation != null) {
                appendLine("Last check on this Wi-Fi: ${date(observation.at)}")
                appendLine("Wi-Fi outcome: ${observation.outcome.name.replace('_', ' ').lowercase()}")
            }
            if (account != null) {
                appendLine("Saved login rejected: ${yesNo(CredentialVault.wasRejected(context, account))}")
                appendLine("Last attempt for this login: ${date(CredentialVault.lastAttempt(context, account))}")
            }
        }.trimEnd()
    }

    private fun yesNo(value: Boolean) = if (value) "Yes" else "No"

    private fun date(at: Long) = if (at <= 0L) "None" else DateFormat.getDateTimeInstance(
        DateFormat.SHORT, DateFormat.SHORT
    ).format(Date(at))

    private fun transport(caps: NetworkCapabilities?) = when {
        caps == null -> "Unavailable"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Other"
    }

    private fun wifiStatus(caps: NetworkCapabilities?) = when {
        caps == null -> "Unavailable"
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) -> "Captive portal"
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "Internet validated"
        else -> "Unverified"
    }
}
