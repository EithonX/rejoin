package org.campus.autoconnect

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Opt-in FortiGate session logout over the explicitly trusted Wi-Fi.
 * Never handles account credentials or authentication tokens.
 */
object LiveSessionTest {
    private const val HOST = "172.16.2.1"
    private const val PORT = 1000

    fun selectedWifiMatches(context: Context): Boolean {
        val snapshot = WifiProbe.current(context) ?: return false
        return TrustedNetworks.find(context, snapshot) != null
    }

    fun logout(context: Context): String {
        val snapshot = WifiProbe.current(context)
            ?: return "Wi-Fi unavailable. No logout request sent."
        if (TrustedNetworks.find(context, snapshot) == null) {
            return "Trusted Wi-Fi does not match. No logout request sent."
        }
        var socket: Socket? = null
        return try {
            socket = snapshot.network.socketFactory.createSocket()
            socket.connect(InetSocketAddress(HOST, PORT), 5000)
            socket.soTimeout = 5000
            val message = "GET /logout? HTTP/1.1\r\n" +
                "Host: " + HOST + ":" + PORT + "\r\n" +
                "User-Agent: Rejoin/2.1\r\n" +
                "Accept: text/html\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().write(message.toByteArray(StandardCharsets.US_ASCII))
            socket.getOutputStream().flush()
            val line = BufferedReader(
                InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)
            ).readLine()?.take(100)
            val status = if (line?.startsWith("HTTP/") == true) line else "No valid HTTP status"
            "CU logout request sent once.\nServer: " + status +
                "\nThis does not prove the session ended. Tap Verify internet; if needed, open the CU sign-in page."
        } catch (e: Exception) {
            "Logout not confirmed (" + e.javaClass.simpleName +
                "). Do not repeat blindly; verify internet status first."
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
