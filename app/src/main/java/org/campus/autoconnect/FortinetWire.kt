package org.campus.autoconnect

import android.net.Network
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Fixed-destination, network-bound HTTP/1.1 client for the verified CU FortiGate page.
 * This campus endpoint uses unencrypted HTTP; credentials MUST be sent only after opt-in.
 * No response bodies, tokens, passwords or cookies are written to logs.
 */
object FortinetWire {
    private const val HOST = "172.16.2.1"
    private const val PORT = 1000
    data class Response(val code: Int, val body: String)
    data class Submission(val submitted: Boolean, val description: String)
    private val attrs = Regex("""([A-Za-z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)

    private fun attributes(tag: String): Map<String, String> =
        attrs.findAll(tag).associate { match ->
            match.groupValues[1].lowercase() to
                (match.groupValues[2].ifEmpty { match.groupValues[3].ifEmpty { match.groupValues[4] } })
        }

    private fun fieldValues(html: String): Map<String, String> =
        Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(html).map { attributes(it.value) }
            .filter { it.containsKey("name") }
            .associate { it["name"]!!.lowercase() to (it["value"] ?: "") }

    private fun request(network: Network, method: String, path: String, body: ByteArray? = null): Response? {
        // Never accept untrusted URLs or redirects as authentication destinations.
        if (method !in setOf("GET", "POST") || path !in setOf("/login?", "/")) return null
        var socket: java.net.Socket? = null
        return try {
            socket = network.socketFactory.createSocket()
            socket.connect(InetSocketAddress(HOST, PORT), 3500)
            socket.soTimeout = 3500
            val headers = StringBuilder()
                .append(method).append(" ").append(path).append(" HTTP/1.1\r\n")
                .append("Host: ").append(HOST).append(":").append(PORT).append("\r\n")
                .append("Connection: close\r\n")
                .append("Accept: text/html\r\nAccept-Encoding: identity\r\n")
                .append("User-Agent: CampusAutoConnect/0.5\r\n")
            if (body != null) {
                headers.append("Content-Type: application/x-www-form-urlencoded\r\n")
                    .append("Content-Length: ").append(body.size).append("\r\n")
                    .append("Origin: http://").append(HOST).append(":").append(PORT).append("\r\n")
                    .append("Referer: http://").append(HOST).append(":").append(PORT).append("/login?\r\n")
            }
            headers.append("\r\n")
            socket.getOutputStream().write(headers.toString().toByteArray(StandardCharsets.US_ASCII))
            if (body != null) socket.getOutputStream().write(body)
            socket.getOutputStream().flush()

            val out = ByteArrayOutputStream()
            val buffer = ByteArray(2048)
            while (out.size() < 65536) {
                val n = try {
                    socket.getInputStream().read(buffer, 0, minOf(buffer.size, 65536 - out.size()))
                } catch (e: java.net.SocketTimeoutException) {
                    if (out.size() == 0) throw e
                    break
                } catch (e: java.net.SocketException) {
                    if (out.size() == 0) throw e
                    break
                }
                if (n < 0) break
                if (n > 0) out.write(buffer, 0, n)
            }
            val response = out.toString(StandardCharsets.ISO_8859_1.name())
            val split = response.indexOf("\r\n\r\n")
            if (split < 0) return null
            val first = response.substringBefore("\r\n")
            val status = Regex("""^HTTP/\d\.\d\s+(\d{3})""").find(first)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val header = response.substring(0, split)
            // CU currently sends an ordinary HTML response. Never misparse chunked data.
            if (header.contains("Transfer-Encoding: chunked", ignoreCase = true)) return null
            Response(status, response.substring(split + 4))
        } catch (_: Exception) { null }
        finally { try { socket?.close() } catch (_: Exception) {} }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    fun submitOnce(network: Network, username: String, password: String, report: (String) -> Unit = {}): Submission {
        report("Stage 1/3: Fetching CU login form")
        val login = request(network, "GET", "/login?")
            ?: return Submission(false, "GET /login? failed: no valid HTTP response")
        if (login.code != 200 || !login.body.contains("Firewall Authentication", ignoreCase = true)) {
            return Submission(false, "Unexpected login page; no password sent")
        }
        val form = Regex("""<form\b[^>]*>""", RegexOption.IGNORE_CASE).find(login.body)?.value
            ?: return Submission(false, "Login form not recognized")
        val formAttrs = attributes(form)
        if (!formAttrs["method"].equals("post", true) || formAttrs["action"] != "/") {
            return Submission(false, "Unexpected form method or target")
        }
        val fields = fieldValues(login.body)
        if (!listOf("username", "password", "magic", "4tredir").all { fields.containsKey(it) }) {
            return Submission(false, "Required login fields missing")
        }
        val magic = fields["magic"].orEmpty()
        if (magic.isBlank() || magic.length > 2048) return Submission(false, "Missing authentication state token")
        val redirect = fields["4tredir"].orEmpty().take(1024)
        val data = "username=" + encode(username) + "&password=" + encode(password) +
            "&magic=" + encode(magic) + "&4Tredir=" + encode(redirect)
        report("Stage 2/3: Sending authorized CU login form")
        val bytes = data.toByteArray(StandardCharsets.UTF_8)
        val response = request(network, "POST", "/", bytes)
        bytes.fill(0)
        // Even if the server closes early, the POST may have been processed.
        // Connectivity verification is the only success criterion.
        return when {
            response == null -> Submission(true, "POST sent; response missing or malformed")
            response.code in listOf(200, 302, 303) -> Submission(true, "POST submitted; HTTP " + response.code)
            else -> Submission(false, "POST returned HTTP " + response.code)
        }
    }
}
