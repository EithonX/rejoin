package org.campus.autoconnect

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.HttpURLConnection
import java.net.URL

object WifiProbe {
    private const val PROBE_URL = "http://connectivitycheck.gstatic.com/generate_204"
    data class Snapshot(val network: Network, val ssid: String, val bssid: String)
    data class Result(val status: String, val code: Int?, val matched: Boolean)

    @Suppress("DEPRECATION")
    fun current(context: Context): Snapshot? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = try { wifi.connectionInfo } catch (_: Exception) { return null }
        val ssid = info?.ssid?.removeSurrounding("\"") ?: return null
        val bssid = info.bssid ?: return null
        if (ssid == WifiManager.UNKNOWN_SSID || ssid.isBlank() || bssid.equals("02:00:00:00:00:00", true)) return null
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return null
        return Snapshot(network, ssid, bssid)
    }

    fun trust(context: Context, snapshot: Snapshot) {
        context.getSharedPreferences("profile", Context.MODE_PRIVATE).edit()
            .putString("ssid", snapshot.ssid).putString("bssid", snapshot.bssid).apply()
    }

    fun describe(context: Context): String {
        val p = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val ssid = p.getString("ssid", null) ?: return "No trusted network"
        return "Trusted SSID: $ssid\nBSSID: " + p.getString("bssid", "")
    }

    fun probe(context: Context): Result {
        val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val snapshot = current(context) ?: return Result("Wi-Fi identity unavailable", null, false)
        if (snapshot.ssid != prefs.getString("ssid", null) ||
            !snapshot.bssid.equals(prefs.getString("bssid", null), true)) {
            return Result("Not the selected Wi-Fi", null, false)
        }
        var c: HttpURLConnection? = null
        return try {
            c = snapshot.network.openConnection(URL(PROBE_URL)) as HttpURLConnection
            c.connectTimeout = 4000
            c.readTimeout = 4000
            c.instanceFollowRedirects = false
            c.useCaches = false
            c.setRequestProperty("Cache-Control", "no-cache")
            val code = c.responseCode
            val location = c.getHeaderField("Location")
            val redirectHost = try { location?.let { URL(URL(PROBE_URL), it).host } } catch (_: Exception) { null }
            val state = when {
                code == 204 -> "Online"
                code in listOf(301, 302, 303, 307, 308) && redirectHost == "172.16.2.1" -> "Captive portal detected"
                code == 200 -> "Unexpected response; possible interception"
                code == 503 -> "Network outage"
                else -> "Unknown response"
            }
            Result(state, code, true)
        } catch (_: Exception) {
            Result("Connection failed; could be outage", null, true)
        } finally {
            c?.disconnect()
        }
    }
}
