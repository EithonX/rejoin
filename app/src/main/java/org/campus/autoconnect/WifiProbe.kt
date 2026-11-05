package org.campus.autoconnect

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.io.ByteArrayOutputStream
import android.os.Build
import android.net.wifi.WifiInfo
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

    /**
     * Resolve the real Wi-Fi Network rather than trusting Android's default route,
     * which may be cellular or a VPN while CU authentication is expired.
     * Prefer modern per-Network WifiInfo; use the legacy API only if unambiguous.
     */
    @Suppress("DEPRECATION")
    fun current(context: Context, requiredNetwork: Network? = null): Snapshot? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return null
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val candidates = if (requiredNetwork != null) listOf(requiredNetwork)
            else cm.allNetworks.toList()
        val wifiNetworks = candidates.filter { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        if (wifiNetworks.isEmpty()) return null
        fun normalize(raw: String?): String? {
            val value = raw?.removeSurrounding("\"")?.trim().orEmpty()
            if (value.isBlank() || value == WifiManager.UNKNOWN_SSID ||
                value == "<unknown ssid>") return null
            return value
        }
        fun validBssid(value: String?): Boolean {
            return !value.isNullOrBlank() && !value.equals("02:00:00:00:00:00", true)
        }
        val trustedProfiles = TrustedNetworks.all(context)
        var fallback: Snapshot? = null
        for (network in wifiNetworks) {
            val cap = cm.getNetworkCapabilities(network)
            val modern = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cap?.transportInfo as? WifiInfo else null
            val ssid = normalize(modern?.ssid)
            val bssid = modern?.bssid
            if (ssid != null && validBssid(bssid)) {
                val snap = Snapshot(network, ssid, bssid!!)
                if (trustedProfiles.any { TrustedNetworks.matches(it, ssid, bssid!!) }) return snap
                fallback = fallback ?: snap
            }
        }
        if (fallback != null) return fallback
        // API 31+ can redact transportInfo if background location isn't allowed.
        // Avoid attributing Wi-Fi identity to an arbitrary network in multi-Wi-Fi mode.
        if (wifiNetworks.size != 1) return null
        val info = try {
            val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            manager.connectionInfo
        } catch (_: Exception) { null } ?: return null
        val ssid = normalize(info.ssid) ?: return null
        if (!validBssid(info.bssid)) return null
        return Snapshot(wifiNetworks.single(), ssid, info.bssid)
    }

    fun trust(context: Context, snapshot: Snapshot) {
        TrustedNetworks.saveCurrent(context, snapshot)
    }

    fun describe(context: Context): String {
        val all = TrustedNetworks.all(context)
        if (all.isEmpty()) return "No trusted networks"
        return all.joinToString("\n") { it.label + " · " + it.ssid }
    }

    private data class ProbeHttp(val code: Int, val response: String)
    private fun request(network: Network, ip: String, host: String): ProbeHttp? {
        var socket: java.net.Socket? = null
        return try {
            val ipv4 = ip.split('.').map { it.toIntOrNull() ?: return null }
            if (ipv4.size != 4 || ipv4.any { it !in 0..255 }) return null
            val address = InetAddress.getByAddress(ipv4.map { it.toByte() }.toByteArray())
            socket = network.socketFactory.createSocket()
            socket.connect(InetSocketAddress(address, 80), 2500)
            socket.soTimeout = 2500
            val req = "GET /generate_204 HTTP/1.1\r\nHost: " + host +
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

    /**
     * A healthy connectivity endpoint returns 204. Redirects and branded login HTML
     * are captive-portal evidence. Unexpected statuses alone are NOT authorization
     * to send credentials. Retest alternate endpoints before declaring an outage.
     */
    fun probe(context: Context, allowDns: Boolean = false): Result {
        val pref = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val snapshot = current(context) ?: return Result("Wi-Fi identity unavailable", null, false)
        if (TrustedNetworks.find(context, snapshot) == null) {
            return Result("Not a trusted Wi-Fi", null, false)
        }
        val targets = listOf(CHECK_HOST, "clients3.google.com")
        val findings = mutableListOf<String>()
        var portalCode: Int? = null
        var portalEvidence = false
        var lastCode: Int? = null
        var hasOutage = false

        for (host in targets) {
            val cacheKey = if (host == CHECK_HOST) "probe_ip" else "probe_ip_backup"
            val cache = pref.getString(cacheKey, null)
            // Hot path: never invoke network.getAllByName(). With Private DNS and
            // a captive portal, name resolution can block far longer than socket timeouts.
            // Only the explicit initial check may resolve an address for caching.
            val resolved = if (cache == null && allowDns) try {
                snapshot.network.getAllByName(host).filterIsInstance<Inet4Address>()
                    .firstOrNull()?.hostAddress
            } catch (_: Exception) { null } else null
            val ips = listOfNotNull(cache, resolved).take(1)
            if (ips.isEmpty()) {
                findings.add("$host: DNS unavailable")
                continue
            }
            for (ip in ips) {
                val response = request(snapshot.network, ip, host)
                if (response == null) {
                    findings.add("$host: no HTTP response")
                    continue
                }
                val code = response.code
                lastCode = code
                if (code == 204) {
                    pref.edit().putString(cacheKey, ip).apply()
                    return Result("Online", 204, true)
                }

                val headers = response.response.substringBefore("\r\n\r\n")
                val location = Regex("""(?im)^Location:\s*([^\r\n]+)""")
                    .find(headers)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                val locationUri = try { java.net.URI(location) } catch (_: Exception) { null }
                val redirectHost = locationUri?.host.orEmpty().lowercase()
                val body = response.response.substringAfter("\r\n\r\n", "")
                val fortinetFields = body.contains("name=\"magic\"", true) &&
                    (body.contains("name=\"username\"", true) || body.contains("Firewall Authentication", true))
                val captive = when {
                    code in 300..399 -> {
                        // generate_204 never normally redirects; the server may use
                        // a hostname rather than CU's numerical gateway IP.
                        location.isNotBlank() && (
                            redirectHost != host.lowercase() ||
                            location.contains("login", true) ||
                            location.contains("fgtauth", true) ||
                            location.contains("172.16.", true) ||
                            location.contains(":1000")
                        )
                    }
                    code == 200 -> fortinetFields ||
                        (body.contains("<form", true) && body.contains("password", true))
                    else -> false
                }
                findings.add("$host: HTTP $code" + if (code in 300..399) " redirect" else "")
                if (captive) {
                    portalEvidence = true
                    portalCode = code
                } else if (code == 503) hasOutage = true
            }
        }
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(snapshot.network)
        val androidCaptive = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true
        return when {
            portalEvidence || androidCaptive -> Result("Captive portal detected", portalCode ?: lastCode, true)
            hasOutage -> Result("Network outage or upstream service error", lastCode, true)
            else -> Result("Unconfirmed network state (" +
                findings.take(3).joinToString("; ").take(180) + "); no automatic login", lastCode, true)
        }
    }
}
