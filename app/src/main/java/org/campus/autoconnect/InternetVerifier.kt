package org.campus.autoconnect

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/**
 * IP-only internet verification bound to the selected Wi-Fi Network.
 * No DNS resolution, mobile-data fallback or relaxed certificate checks.
 */
object InternetVerifier {
    enum class State { ONLINE, PORTAL, UNKNOWN }
    data class Result(val state: State, val details: String)

    fun check(context: Context): Result {
        if (!LiveSessionTest.selectedWifiMatches(context))
            return Result(State.UNKNOWN, "Trusted Wi-Fi not connected; no test sent")
        val snapshot = WifiProbe.current(context)
            ?: return Result(State.UNKNOWN, "Wi-Fi identity unavailable")
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(snapshot.network)
        val captive = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val errors = mutableListOf<String>()

        // Prefer verified HTTPS to a public IP. These addresses require no DNS.
        for (ip in listOf("1.1.1.1", "1.0.0.1")) {
            var conn: HttpsURLConnection? = null
            try {
                conn = snapshot.network.openConnection(URL("https://" + ip + "/cdn-cgi/trace")) as HttpsURLConnection
                conn.connectTimeout = 2600
                conn.readTimeout = 2600
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                val code = conn.responseCode
                if (code == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText().take(4096) }
                    if (body.contains("colo=") && body.contains("http="))
                        return Result(State.ONLINE, "Public HTTPS reachable over Wi-Fi (IP-only: " + ip + ")")
                }
                errors.add(ip + ": HTTP " + code)
            } catch (e: Exception) {
                errors.add(ip + ": " + e.javaClass.simpleName)
            } finally { conn?.disconnect() }
        }

        // An unauthenticated HTTP probe to a fixed public IP may be intercepted by Fortinet.
        var socket: java.net.Socket? = null
        try {
            socket = snapshot.network.socketFactory.createSocket()
            socket.connect(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)), 80), 2600)
            socket.soTimeout = 2600
            val req = "GET /cdn-cgi/trace HTTP/1.1\r\nHost: 1.1.1.1\r\nConnection: close\r\nAccept: text/plain\r\n\r\n"
            socket.getOutputStream().write(req.toByteArray(StandardCharsets.US_ASCII))
            socket.getOutputStream().flush()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            try {
                while (out.size() < 4096) {
                    val n = socket.getInputStream().read(buf, 0, minOf(buf.size, 4096 - out.size()))
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
            } catch (_: Exception) {}
            val raw = out.toString(StandardCharsets.ISO_8859_1.name())
            val header = raw.substringBefore("\r\n\r\n")
            val body = raw.substringAfter("\r\n\r\n", "")
            val status = header.substringBefore("\r\n")
            val location = header.lineSequence().firstOrNull { it.startsWith("Location:", true) }
                ?.substringAfter(':')?.trim().orEmpty()
            if (status.contains(" 200 ") && body.contains("colo=") && body.contains("http="))
                return Result(State.ONLINE, "Public HTTP reachable over Wi-Fi (IP-only)")
            if (location.contains("172.16.2.1") || location.contains(":1000/login") ||
                body.contains("Firewall Authentication", true) ||
                (body.contains("name=\"magic\"", true) && body.contains("password", true)))
                return Result(State.PORTAL, "FortiGate intercepted public IP request")
        } catch (_: Exception) {
            errors.add("Public IP HTTP check unavailable")
        } finally { try { socket?.close() } catch (_: Exception) {} }

        if (captive) return Result(State.PORTAL, "Android indicates a captive portal")
        return Result(State.UNKNOWN, "No independent internet proof. Android validated=" +
            validated + "; " + errors.take(2).joinToString(", "))
    }
}
