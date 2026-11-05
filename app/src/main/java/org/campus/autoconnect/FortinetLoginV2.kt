package org.campus.autoconnect

import android.content.Context
import android.net.Network
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Native FortiGate login client. Only targets the configured CU gateway on the
 * explicitly trusted Wi-Fi. Does not blindly follow cross-host redirects.
 */
object FortinetLoginV2 {
    private const val GATEWAY = "172.16.2.1"
    private const val PORT = 1000
    private const val CHECK_HOST = "connectivitycheck.gstatic.com"

    data class Outcome(val attempted: Boolean, val summary: String)
    private data class Reply(val code: Int, val headers: String, val body: String) {
        fun header(name: String): String = headers.lineSequence()
            .firstOrNull { it.substringBefore(':').equals(name, ignoreCase = true) }
            ?.substringAfter(':', "")?.trim().orEmpty()
        fun cookie(): String = headers.lineSequence()
            .filter { it.startsWith("Set-Cookie:", ignoreCase = true) }
            .map { it.substringAfter(':').trim().substringBefore(';') }
            .filter { it.isNotBlank() && !it.contains('\r') && !it.contains('\n') }
            .joinToString("; ")
    }

    private fun validIpv4(ip: String): Boolean {
        val parts = ip.split('.')
        return parts.size == 4 && parts.all { p ->
            p.isNotEmpty() && p.length < 4 && p.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    /** Strictly bounded HTTP/1.1 client to handle CU's incomplete close behavior. */
    private fun http(
        network: Network,
        destination: String,
        port: Int,
        host: String,
        path: String,
        method: String = "GET",
        body: ByteArray? = null,
        cookie: String = ""
    ): Reply? {
        if (!validIpv4(destination) || port !in listOf(80, PORT)) return null
        if (host != GATEWAY && host != CHECK_HOST) return null
        if (method !in listOf("GET", "POST")) return null
        if (method == "POST" && (destination != GATEWAY || port != PORT)) return null
        if (path.contains('\r') || path.contains('\n') || !path.startsWith("/")) return null

        var socket: java.net.Socket? = null
        return try {
            socket = network.socketFactory.createSocket()
            val ip = InetAddress.getByAddress(destination.split('.').map { it.toInt().toByte() }.toByteArray())
            socket.connect(InetSocketAddress(ip, port), 3500)
            socket.soTimeout = 3500
            val h = StringBuilder()
                .append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                .append("Host: ").append(host)
            if (port != 80) h.append(':').append(port)
            h.append("\r\nConnection: close\r\nAccept: text/html\r\nAccept-Encoding: identity\r\n")
                .append("User-Agent: Mozilla/5.0 (Android; CampusAutoConnect)\r\n")
            if (cookie.isNotBlank()) h.append("Cookie: ").append(cookie).append("\r\n")
            if (body != null) h.append("Content-Type: application/x-www-form-urlencoded\r\n")
                .append("Content-Length: ").append(body.size).append("\r\n")
            h.append("\r\n")
            socket.getOutputStream().write(h.toString().toByteArray(StandardCharsets.US_ASCII))
            if (body != null) socket.getOutputStream().write(body)
            socket.getOutputStream().flush()

            val buf = ByteArray(2048)
            val out = ByteArrayOutputStream()
            while (out.size() < 98304) {
                val n = try {
                    socket.getInputStream().read(buf, 0, minOf(buf.size, 98304 - out.size()))
                } catch (e: java.net.SocketTimeoutException) {
                    if (out.size() == 0) throw e
                    break
                } catch (e: java.net.SocketException) {
                    if (out.size() == 0) throw e
                    break
                }
                if (n <= 0) break
                out.write(buf, 0, n)
            }
            val text = out.toString(StandardCharsets.ISO_8859_1.name())
            val cut = text.indexOf("\r\n\r\n")
            if (cut < 0) return null
            val headers = text.substring(0, cut)
            val code = Regex("""^HTTP/\d(?:\.\d)?\s+(\d{3})""")
                .find(headers)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
            var payload = text.substring(cut + 4)
            if (headers.contains("Transfer-Encoding: chunked", ignoreCase = true)) {
                payload = unchunk(payload) ?: return null
            }
            Reply(code, headers, payload.take(65536))
        } catch (_: Exception) {
            null
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun unchunk(raw: String): String? {
        val output = StringBuilder()
        var pos = 0
        repeat(100) {
            val eol = raw.indexOf("\r\n", pos)
            if (eol < 0) return null
            val size = raw.substring(pos, eol).substringBefore(';').trim()
                .toIntOrNull(16) ?: return null
            if (size == 0) return output.toString()
            pos = eol + 2
            if (size < 0 || size > 65536 || pos + size + 2 > raw.length) return null
            output.append(raw, pos, pos + size)
            pos += size + 2
        }
        return null
    }

    private val attrRegex = Regex("""([A-Za-z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
    private fun attrs(tag: String): Map<String, String> =
        attrRegex.findAll(tag).associate { it.groupValues[1].lowercase() to
            (it.groupValues[2].ifEmpty { it.groupValues[3].ifEmpty { it.groupValues[4] } })
        }

    private fun htmlUnescape(value: String): String = value.replace("&amp;", "&")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

    private fun formValues(html: String): List<Pair<String, String>> =
        Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .map { attrs(it.value) }
            .filter { it.containsKey("name") }
            .map { (it["name"] ?: "") to htmlUnescape(it["value"] ?: "") }
            .toList()

    private fun gatewayPath(url: String): String? {
        return try {
            val uri = URI(htmlUnescape(url).trim().replace(" ", "%20"))
            if (!uri.scheme.equals("http", true) ||
                uri.host != GATEWAY || uri.port != PORT) return null
            val path = uri.rawPath.ifBlank { "/" }
            if (!(path == "/" || path.startsWith("/login") || path.startsWith("/fgtauth") ||
                    path.startsWith("/portal"))) return null
            path + (if (uri.rawQuery.isNullOrEmpty()) "" else "?" + uri.rawQuery)
        } catch (_: Exception) { null }
    }

    private fun redirectFrom(reply: Reply): String? {
        val header = reply.header("Location")
        if (header.isNotBlank()) {
            gatewayPath(header)?.let { return it }
        }
        val embedded = Regex("""http://172\.16\.2\.1:1000/[^\s"'<>\\]+""", RegexOption.IGNORE_CASE)
            .find(reply.body)?.value
        return embedded?.let { gatewayPath(it) }
    }

    private data class Page(val reply: Reply, val source: String, val cookies: String)

    private fun hasFreshMagic(html: String): Boolean {
        val values = formValues(html)
        return values.firstOrNull { it.first.equals("magic", true) }
            ?.second?.isNotBlank() == true
    }

    private fun getPage(network: Network, context: Context, report: (String) -> Unit): Page? {
        val pref = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val cachedIp = pref.getString("probe_ip", null)
        if (cachedIp != null && validIpv4(cachedIp)) {
            report("Fetching login session from the captive redirect")
            val start = http(network, cachedIp, 80, CHECK_HOST, "/generate_204")
            if (start != null) {
                if (start.code == 204) return null // Someone has already restored internet
                val path = redirectFrom(start)
                if (path != null) {
                    val redirected = http(network, GATEWAY, PORT, GATEWAY, path)
                    if (redirected?.code == 200 && hasFreshMagic(redirected.body)) {
                        return Page(redirected, "captive redirect", redirected.cookie())
                    }
                }
                if (start.code == 200 && hasFreshMagic(start.body)) {
                    return Page(start, "intercepted HTML", start.cookie())
                }
            }
        }
        report("Reading the official CU login form")
        val direct = http(network, GATEWAY, PORT, GATEWAY, "/login?") ?: return null
        return if (direct.code == 200 && hasFreshMagic(direct.body))
            Page(direct, "direct login", direct.cookie()) else null
    }

    fun submitOnce(
        context: Context, network: Network,
        username: String, password: String, report: (String) -> Unit = {}
    ): Outcome {
        report("Stage 1: Resolving a fresh CU authentication session")
        val page = getPage(network, context, report)
            ?: return Outcome(false, "No usable fresh magic token; no password sent. Login page or redirect unavailable.")
        val form = Regex("""<form\b[^>]*>""", RegexOption.IGNORE_CASE)
            .find(page.reply.body)?.value
            ?: return Outcome(false, "No login form detected; no password sent")
        val formAttrs = attrs(form)
        val action = formAttrs["action"] ?: "/"
        val target = if (action == "/") "/" else gatewayPath(action)
        if (!formAttrs["method"].equals("post", true) || target == null) {
            return Outcome(false, "Unexpected form destination; no password sent")
        }
        val fields = formValues(page.reply.body)
        if (!listOf("magic", "username", "password").all { key ->
                fields.any { it.first.equals(key, true) }
            }) return Outcome(false, "Required fields missing; no password sent")
        if (!hasFreshMagic(page.reply.body)) return Outcome(false, "Empty session token; no password sent")
        val params = mutableListOf<Pair<String, String>>()
        for ((name, value) in fields) {
            when {
                name.equals("username", true) -> params.add("username" to username)
                name.equals("password", true) -> params.add("password" to password)
                name.isNotBlank() -> params.add(name to value)
            }
        }
        val encoded = params.joinToString("&") { pair ->
            URLEncoder.encode(pair.first, "UTF-8") + "=" +
                URLEncoder.encode(pair.second, "UTF-8")
        }
        val bytes = encoded.toByteArray(StandardCharsets.UTF_8)
        report("Stage 2: Submitting the FortiGate form")
        val reply = http(network, GATEWAY, PORT, GATEWAY, target, "POST", bytes, page.cookies)
        bytes.fill(0)
        // Do not infer a successful login solely from a 200 or 302.
        if (reply == null) {
            return Outcome(true, "POST attempted; server response was incomplete. Form source: " + page.source)
        }
        val body = reply.body
        val rejected = listOf("invalid password", "invalid username", "authentication failed",
            "login failed", "incorrect password", "incorrect username").any { body.contains(it, true) }
        val keepalive = body.contains("keepalive", true)
        val stillLogin = body.contains("Firewall Authentication", true)
        return Outcome(true, "POST HTTP " + reply.code + "; form source: " + page.source +
            "; server: " + when {
                rejected -> "rejected credentials"
                keepalive -> "keepalive response"
                stillLogin -> "login form returned"
                reply.code in 300..399 -> "redirect returned"
                else -> "other response"
            })
    }
}
