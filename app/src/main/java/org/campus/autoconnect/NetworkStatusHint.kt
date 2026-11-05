package org.campus.autoconnect

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Ask Android to revisit its stale "Sign in to Wi-Fi" indicator after the app
 * has independently proven the selected Wi-Fi has working public internet.
 * The framework makes the final validation decision.
 */
object NetworkStatusHint {
    fun requestAfterVerifiedOnline(context: Context): Boolean {
        if (!LiveSessionTest.selectedWifiMatches(context)) return false
        val p = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - p.getLong("last_validation_hint", 0L) < 120000L) return false
        val network = WifiProbe.current(context)?.network ?: return false
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (cm.getNetworkCapabilities(network)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) return false
        return try {
            cm.reportNetworkConnectivity(network, true)
            p.edit().putLong("last_validation_hint", now).apply()
            true
        } catch (_: Exception) { false }
    }
}
