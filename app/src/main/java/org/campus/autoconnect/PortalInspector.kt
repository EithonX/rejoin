package org.campus.autoconnect

import android.content.Context
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Read-only Fortinet portal diagnostics, bound to the selected Wi-Fi network.
 * Does not submit credentials, capture cookies or reveal token values.
 */
object PortalInspector {
    private const val HOST = "172.16.2.1"
    private const val PORT = 1000
    private const val URL_TEXT = "http://172.16.2.1:1000/login?"

    fun inspect(context: Context): String {
        val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val snapshot = WifiProbe.current(context) ?: return "Wi-Fi identity unavailable; no request sent."
        if (snapshot.ssid != prefs.getString("ssid", null) ||
            !snapshot.bssid.equals(prefs.getString("bssid", null), ignoreCase = true)) {
            return "Not the selected Wi-Fi; no request sent."
        }

        // One GET via the proven Wi-Fi-bound socket path. No duplicate HTTP attempt.
        val result = rawHttp(snapshot.network.socketFactory)
        return "Selected Wi-Fi matched.\n" + result +
            "\nRead-only inspection. No passwords, cookies or token values retained."
    }

    private fun summarize(html: String): String {
        val title = Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.trim()?.take(80) ?: "Unknown"
        val fields = Regex("""<input\b[^>]*\bname\s*=\s*['"]?([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.groupValues[1] }.distinct().toList()
        val expected = listOf("username", "password", "magic", "4Tredir")
        return "Title: $title\n" +
            "FortiGate-form match: " + expected.all { it in fields } + "\n" +
            "Expected input names present: " + expected.filter { it in fields }.joinToString(", ").ifEmpty { "none" } + "\n"
    }

    private fun rawHttp(factory: javax.net.SocketFactory): String {
        var socket: Socket? = null
        return try {
            socket = factory.createSocket()
            socket.connect(InetSocketAddress(HOST, PORT), 5000)
            socket.soTimeout = 5000
            val request = "GET /login? HTTP/1.1\r\nHost: $HOST:$PORT\r\nAccept: text/html\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().write(request.toByteArray(StandardCharsets.US_ASCII))
            socket.getOutputStream().flush()
            val out = ByteArrayOutputStream()
            val bytes = ByteArray(2048)
            val maxBytes = 24576
            try {
                while (out.size() < maxBytes) {
                    val n = socket.getInputStream().read(bytes, 0, minOf(bytes.size, maxBytes - out.size()))
                    if (n < 0) break
                    if (n > 0) out.write(bytes, 0, n)
                }
            } catch (_: java.net.SocketTimeoutException) {
                if (out.size() == 0) return "TCP connected, but response timed out."
            }
            if (out.size() == 0) return "TCP connected; remote server sent no data."
            val response = out.toString(StandardCharsets.ISO_8859_1.name())
            val status = response.lineSequence().firstOrNull()?.trim()?.take(90) ?: "No HTTP status line"
            val bodyStart = response.indexOf("\r\n\r\n")
            if (bodyStart < 0) return "TCP: " + status + "; headers incomplete"
            val body = response.substring(bodyStart + 4)
            val form = if (status.contains(" 200 ")) summarize(body) else "Unexpected status; form not inspected."
            "TCP: " + status + "\n" + form
        } catch (e: Exception) {
            "TCP error: " + safeError(e)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun safeError(e: Exception): String {
        val message = e.message.orEmpty()
        val reason = when {
            message.contains("CLEARTEXT", true) -> "Cleartext HTTP blocked by Android"
            message.contains("unexpected end", true) -> "Unexpected end of HTTP response"
            message.contains("EOF", true) -> "Server closed HTTP response early"
            e is java.net.SocketTimeoutException -> "Timeout"
            e is java.net.ConnectException -> "Connection refused or unreachable"
            e is java.net.SocketException -> "Socket failure or connection reset"
            e is java.net.UnknownHostException -> "Host resolution failed"
            e is java.io.IOException -> "I/O failure"
            else -> "Request failure"
        }
        return reason + " (" + e.javaClass.simpleName + ")"
    }
}

