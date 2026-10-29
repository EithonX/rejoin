package org.campus.autoconnect

import android.content.Context
import android.security.NetworkSecurityPolicy
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
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

        val output = StringBuilder("Selected Wi-Fi matched.\n")
        output.append("Cleartext permitted: ").append(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(HOST)).append("\n")
        var connection: HttpURLConnection? = null
        try {
            connection = snapshot.network.openConnection(URL(URL_TEXT)) as HttpURLConnection
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "text/html")
            connection.setRequestProperty("Connection", "close")
            val code = connection.responseCode
            output.append("Android HTTP: ").append(code).append('\n')
            if (code == 200) {
                val html = connection.inputStream.bufferedReader().use { it.readText().take(12000) }
                output.append(summarize(html))
            } else {
                output.append("HTTP inspection stopped at status ").append(code)
            }
        } catch (e: Exception) {
            output.append("Android HTTP error: ").append(safeError(e)).append('\n')
            output.append("TCP diagnostic:\n").append(rawHttp(snapshot.network.socketFactory))
        } finally {
            connection?.disconnect()
        }
        output.append("\nRead-only. No credentials sent, no logout performed.")
        return output.toString()
    }

    private fun summarize(html: String): String {
        val title = Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.trim()?.take(80) ?: "Unknown"
        val fields = Regex("""<input\b[^>]*\bname\s*=\s*['"]?([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.groupValues[1] }.distinct().toList()
        val expected = listOf("username", "password", "magic", "4Tredir")
        return "Title: $title\nFortinet marker: ${html.contains("Fortinet", ignoreCase = true)}\n" +
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

