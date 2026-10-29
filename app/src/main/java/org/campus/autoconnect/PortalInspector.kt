package org.campus.autoconnect

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/**
 * GET-only, bounded inspection of a specific campus portal.
 * Never extracts or stores credentials, hidden-token values or cookies.
 */
object PortalInspector {
    private const val PORTAL_HOST = "172.16.2.1"
    private const val PORTAL_URL = "http://172.16.2.1:1000/login?"
    fun inspect(context: Context): String {
        val profile = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val snapshot = WifiProbe.current(context) ?: return "Wi-Fi identity unavailable"
        if (snapshot.ssid != profile.getString("ssid", null) ||
            !snapshot.bssid.equals(profile.getString("bssid", null), ignoreCase = true)
        ) return "Not the selected Wi-Fi. No request sent."

        var conn: HttpURLConnection? = null
        return try {
            conn = snapshot.network.openConnection(URL(PORTAL_URL)) as HttpURLConnection
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.useCaches = false
            val code = conn.responseCode
            if (code != 200) return "Portal HTTP $code; no login attempt."
            val contentType = conn.contentType ?: ""
            if (!contentType.contains("text/html", ignoreCase = true)) return "Unexpected content type; no login attempt."
            val html = conn.inputStream.bufferedReader().use { it.readText().take(12000) }
            val title = Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.getOrNull(1)?.trim()?.take(90) ?: "Unknown"
            val fields = listOf("username", "password", "magic", "4Tredir").filter { field ->
                Regex("""name\\s*=\\s*['"]?\\Q$field\\E(?:['"\\s>])""", RegexOption.IGNORE_CASE).containsMatchIn(html)
            }
            val signature = if (html.contains("fortinet", ignoreCase = true)) "Fortinet marker present" else "Fortinet marker not found"
            "HTTP 200 on $PORTAL_HOST\nTitle: $title\n$signature\n" +
                "Form fields: " + fields.joinToString(", ").ifEmpty { "not recognized" } +
                "\nRead-only inspection completed. No tokens or values saved."
        } catch (e: Exception) {
            "Portal inspection unavailable: ${e.javaClass.simpleName}. No login attempt."
        } finally {
            conn?.disconnect()
        }
    }
}
