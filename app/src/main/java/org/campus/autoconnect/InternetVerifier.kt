package org.campus.autoconnect

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/**
 * Bounded IP-only check tied to exactly the trusted Wi-Fi Network.
 *
 * Cheap path: a single small HTTP request to 1.1.1.1. FortiGate intercepts
 * unauthenticated HTTP with a portal redirect, while normal service returns
 * public Cloudflare trace data. If ambiguous, use verified public HTTPS.
 *
 * We never send credentials here, never resolve DNS, and never ignore TLS.
 */
object InternetVerifier {
    enum class State { ONLINE, PORTAL, UNKNOWN }
    data class Result(val state: State, val details: String)

    fun check(context: Context, requiredNetwork: Network? = null): Result {
        val snapshot = WifiProbe.current(context, requiredNetwork)
            ?: return Result(State.UNKNOWN, "Wi-Fi identity not available")
        val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        if (snapshot.ssid != prefs.getString("ssid", null) ||
            !snapshot.bssid.equals(prefs.getString("bssid", null), true))
            return Result(State.UNKNOWN, "Not the trusted Wi-Fi; no test sent")

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(snapshot.network)
        val captive = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        val quick = publicHttp(snapshot.network)
        if (quick == State.ONLINE)
            return Result(State.ONLINE, "Public internet verified on Wi-Fi (one IP-only request)")
        if (quick == State.PORTAL)
            return Result(State.PORTAL, "CU portal intercepted the Wi-Fi verification request")

        // HTTPS fallbacks run only if the inexpensive HTTP check is inconclusive.
        val failures = mutableListOf<String>()
        for (ip in arrayOf("1.1.1.1", "1.0.0.1")) {
            var conn: HttpsURLConnection? = null
            try {
                conn = snapshot.network.openConnection(URL("https://$ip/cdn-cgi/trace"))
                    as HttpsURLConnection
                conn.connectTimeout = 2200
                conn.readTimeout = 2200
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                val code = conn.responseCode
                if (code == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText().take(2048) }
                    if (body.contains("colo=") && body.contains("http="))
                        return Result(State.ONLINE, "Verified public HTTPS on Wi-Fi (no DNS)")
                }
                failures.add("HTTP $code")
            } catch (e: Exception) {
                failures.add(e.javaClass.simpleName)
            } finally {
                conn?.disconnect()
            }
        }
        return if (captive)
            Result(State.PORTAL, "Android reports captive Wi-Fi; direct verification inconclusive")
        else
            Result(State.UNKNOWN, "Could not confirm internet or portal (system validated=$validated, " +
                failures.take(2).joinToString(", ") + ")")
    }

    private fun publicHttp(network: Network): State? {
        var socket: java.net.Socket? = null
        return try {
            socket = network.socketFactory.createSocket()
            socket.connect(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)), 80), 2200)
            socket.soTimeout = 2200
            val request = "GET /cdn-cgi/trace HTTP/1.1\r\nHost: 1.1.1.1\r\n" +
                "Accept: text/plain\r\nConnection: close\r\nCache-Control: no-cache\r\n\r\n"
            socket.getOutputStream().write(request.toByteArray(StandardCharsets.US_ASCII))
            socket.getOutputStream().flush()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            try {
                while (out.size() < 4096) {
                    val n = socket.getInputStream().read(buffer, 0, minOf(buffer.size, 4096 - out.size()))
                    if (n <= 0) break
                    out.write(buffer, 0, n)
                }
            } catch (_: Exception) {
                if (out.size() == 0) return null
            }
            val response = out.toString(StandardCharsets.ISO_8859_1.name())
            val headers = response.substringBefore("\r\n\r\n")
            val body = response.substringAfter("\r\n\r\n", "")
            val status = headers.substringBefore("\r\n")
            if (status.contains(" 200 ") && body.contains("colo=") && body.contains("http="))
                return State.ONLINE

            val location = headers.lineSequence()
                .firstOrNull { it.startsWith("Location:", true) }?.substringAfter(':')?.trim()
            val uri = try { URI(location) } catch (_: Exception) { null }
            if (uri?.host == "172.16.2.1" && uri.port == 1000)
                return State.PORTAL
            if (body.contains("Firewall Authentication", true) ||
                (body.contains("name=\"magic\"", true) && body.contains("password", true)))
                return State.PORTAL
            null
        } catch (_: Exception) { null }
        finally { try { socket?.close() } catch (_: Exception) {} }
    }
}
