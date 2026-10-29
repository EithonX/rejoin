package org.campus.autoconnect

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * All probes use the selected Wi-Fi socket, never mobile data.
 * Once a public probe address has returned 204, it is cached: subsequent captive
 * portal detection still works when private DNS cannot resolve names.
 */
object WifiProbe {
    private const val CHECK_HOST = "connectivitycheck.gstatic.com"
    data class Snapshot(val network: Network, val ssid: String, val bssid: String)
    data class Result(val status: String, val code: Int?, val matched: Boolean)

    @Suppress("DEPRECATION")
    fun current(context: Context): Snapshot? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = try { wifi.connectionInfo } catch (_: Exception) { return null }
        val ssid = info?.ssid?.removeSurrounding("\"") ?: return null
        val bssid = info.bssid ?: return null
        if (ssid == WifiManager.UNKNOWN_SSID || ssid.isBlank() ||
            bssid.equals("02:00:00:00:00:00", true)) return null
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return null
        return Snapshot(network, ssid, bssid)
    }

    fun trust(context: Context, snapshot: Snapshot) {
        context.getSharedPreferences("profile", Context.MODE_PRIVATE).edit()
            .putString("ssid", snapshot.ssid).putString("bssid", snapshot.bssid).commit()
    }

    fun describe(context: Context): String {
        val p = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val ssid = p.getString("ssid", null) ?: return "No trusted network"
        return "Trusted SSID: " + ssid + "\nBSSID: " + p.getString("bssid", "")
    }

    private data class ProbeHttp(val code: Int, val response: String)
    private fun request(network: Network, ip: String): ProbeHttp? {
        var socket: java.net.Socket? = null
        return try {
            val ipv4 = ip.split('.').map { it.toIntOrNull() ?: return null }
            if (ipv4.size != 4 || ipv4.any { it !in 0..255 }) return null
            val address = InetAddress.getByAddress(ipv4.map { it.toByte() }.toByteArray())
            socket = network.socketFactory.createSocket()
            socket.connect(InetSocketAddress(address, 80), 4000)
            socket.soTimeout = 4000
            val req = "GET /generate_204 HTTP/1.1\r\nHost: " + CHECK_HOST +
                "\r\nConnection: close\r\nCache-Control: no-cache\r\nAccept-Encoding: identity\r\n\r\n"
            socket.getOutputStream().write(req.toByteArray(StandardCharsets.US_ASCII))
            socket.getOutputStream().flush()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            try {
                while (output.size() < 12288) {
                    val n = socket.getInputStream().read(buffer, 0, minOf(buffer.size, 12288 - output.size()))
                    if (n < 0) break
                    if (n > 0) output.write(buffer, 0, n)
                }
            } catch (_: java.net.SocketTimeoutException) {
                if (output.size() == 0) return null
            } catch (_: java.net.SocketException) {
                if (output.size() == 0) return null
            }
            val response = output.toString(StandardCharsets.ISO_8859_1.name())
            val status = Regex("""^HTTP/\d\.\d\s+(\d{3})""")
                .find(response)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
            ProbeHttp(status, response)
        } catch (_: Exception) { null }
        finally { try { socket?.close() } catch (_: Exception) {} }
    }

    fun probe(context: Context): Result {
        val pref = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val snapshot = current(context) ?: return Result("Wi-Fi identity unavailable", null, false)
        if (snapshot.ssid != pref.getString("ssid", null) ||
            !snapshot.bssid.equals(pref.getString("bssid", null), ignoreCase = true)) {
            return Result("Not the selected Wi-Fi", null, false)
        }
        val cached = pref.getString("probe_ip", null)
        val resolved = try {
            snapshot.network.getAllByName(CHECK_HOST).filterIsInstance<Inet4Address>()
                .firstOrNull()?.hostAddress
        } catch (_: Exception) { null }
        val choices = listOfNotNull(cached, resolved).distinct()
        if (choices.isEmpty()) return Result("Connectivity probe DNS unavailable (no cached IP)", null, true)
        for (address in choices) {
            val response = request(snapshot.network, address) ?: continue
            val code = response.code
            val state = when {
                code == 204 -> "Online"
                code in listOf(301, 302, 303, 307, 308) &&
                    Regex("""(?im)^Location:\s*[^\r\n]*172\.16\.2\.1(?::1000)?(?:/|\?|$)""")
                        .containsMatchIn(response.response) -> "Captive portal detected"
                code == 200 && response.response.contains("Firewall Authentication", ignoreCase = true) -> "Captive portal detected"
                code == 503 -> "Network outage"
                else -> "Unknown response; no automatic login"
            }
            if (code == 204) {
                pref.edit().putString("probe_ip", address).apply()
            }
            return Result(state, code, true)
        }
        return Result("Connectivity probe failed; no automatic login", null, true)
    }
}
