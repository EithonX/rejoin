package com.eithonx.rejoin

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.cert.CertificateException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLPeerUnverifiedException

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
    enum class State { ONLINE, PORTAL, SECURE_PORTAL, CERTIFICATE_ERROR, UNSUPPORTED_PORTAL, UNKNOWN }
    class Result(val state: State, val details: String, internal val portalPath: String? = null)

    fun check(context: Context, requiredNetwork: Network? = null): Result {
        val snapshot = WifiProbe.current(context, requiredNetwork)
            ?: return Result(State.UNKNOWN, "Wi-Fi identity not available")
        if (TrustedNetworks.find(context, snapshot) == null)
            return Result(State.UNKNOWN, "Not a trusted Wi-Fi; no test sent")

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(snapshot.network)
        val captive = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        publicHttp(snapshot.network)?.let { return it }

        // HTTPS fallbacks run only if the inexpensive HTTP check is inconclusive.
        val failures = mutableListOf<String>()
        var certificateFailure = false
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
                    val body = conn.inputStream.bufferedReader().use {
                        val buffer = CharArray(2048)
                        var count = 0
                        while (count < buffer.size) {
                            val read = it.read(buffer, count, buffer.size - count)
                            if (read <= 0) break
                            count += read
                        }
                        String(buffer, 0, count)
                    }
                    if (body.contains("colo=") && body.contains("http="))
                        return Result(State.ONLINE, "Verified public HTTPS on Wi-Fi (no DNS)")
                }
                if (code in 300..399) {
                    redirectProblem(conn.getHeaderField("Location"))?.let { return it }
                }
                failures.add("HTTP $code")
            } catch (e: Exception) {
                if (e is SSLPeerUnverifiedException ||
                    generateSequence<Throwable>(e) { it.cause }.take(8).any { it is CertificateException }) {
                    certificateFailure = true
                }
                failures.add(e.javaClass.simpleName)
            } finally {
                conn?.disconnect()
            }
        }
        return if (certificateFailure)
            Result(State.CERTIFICATE_ERROR, "Certificate verification failed during the HTTPS check")
        else if (captive)
            Result(State.PORTAL, "Android reports captive Wi-Fi; direct verification inconclusive")
        else
            Result(State.UNKNOWN, "Could not confirm internet or portal (system validated=$validated, " +
                failures.take(2).joinToString(", ") + ")")
    }

    private fun publicHttp(network: Network): Result? {
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
            interpretPublicHttp(headers, body)
        } catch (_: Exception) { null }
        finally { try { socket?.close() } catch (_: Exception) {} }
    }
    internal fun interpretPublicHttp(headers: String, body: String): Result? {
        val status = headers.substringBefore("\r\n")
        if (status.contains(" 200 ") && body.contains("colo=") && body.contains("http="))
            return Result(State.ONLINE, "Public internet verified on Wi-Fi (one IP-only request)")

        val location = headers.lineSequence()
            .firstOrNull { it.startsWith("Location:", true) }?.substringAfter(':')?.trim()
        val code = status.substringAfter(' ').substringBefore(' ').toIntOrNull() ?: 0
        if (code in 300..399) {
            redirectProblem(location)?.let { return it }
        }
        val path = FortinetPortal.redirectPath(location, body)
        if (path != null || body.contains("Firewall Authentication", true) ||
            (body.contains("name=\"magic\"", true) && body.contains("password", true))) {
            return Result(State.PORTAL, "Sign-in required on this Wi-Fi", path)
        }
        return null
    }
    private fun redirectProblem(location: String?): Result? {
        if (location.isNullOrBlank()) return null
        if (FortinetPortal.isSecureRedirect(location))
            return Result(State.SECURE_PORTAL, "Campus portal redirected to HTTPS")
        if (FortinetPortal.requestPath(location) == null)
            return Result(State.UNSUPPORTED_PORTAL, "Sign-in redirected to an unrecognized address")
        return null
    }
}
