package org.campus.autoconnect

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * Conservative, bounded session recovery. Never sends login credentials without
 * explicit HTTP risk opt-in and a positively recognized captive-portal state.
 */
object SessionEngine {
    private val running = AtomicBoolean(false)
    private const val COOLDOWN_MS = 20 * 60 * 1000L

    fun run(context: Context, userInitiated: Boolean = false): String {
        if (!running.compareAndSet(false, true)) return "Another check is already running."
        val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        fun finish(message: String): String {
            prefs.edit().putString("last_result", message)
                .putLong("last_checked", System.currentTimeMillis()).apply()
            return message
        }
        try {
            val state = WifiProbe.probe(context)
            if (!state.matched) return finish(state.status + "; no action")
            if (state.status == "Online") return finish("Online — no authentication needed")
            if (state.status != "Captive portal detected") {
                return finish(state.status + " — not an authenticated portal failure")
            }
            if (!prefs.getBoolean("auto_enabled", false) || !prefs.getBoolean("http_opt_in", false)) {
                return finish("CU portal detected; automatic login is disabled")
            }
            if (!CredentialVault.hasCredentials(context)) return finish("CU portal detected; credentials not configured")
            val now = System.currentTimeMillis()
            if (now < prefs.getLong("next_allowed", 0L)) {
                return finish("CU portal detected; retry cooldown active")
            }

            // Unpaired clients share an upstream session. Random stagger + a second
            // connectivity check avoids most duplicate logins without LAN chatter.
            if (!userInitiated) {
                Thread.sleep(Random.nextLong(8000, 56000))
            } else {
                Thread.sleep(900)
            }
            val again = WifiProbe.probe(context)
            if (again.status == "Online") return finish("Another device restored internet; no login needed")
            if (again.status != "Captive portal detected") {
                return finish("Portal no longer confirmed; did not submit credentials")
            }

            // Persist before POST, so a killed process will not cause a login storm.
            prefs.edit().putLong("next_allowed", System.currentTimeMillis() + COOLDOWN_MS)
                .putLong("last_attempt", System.currentTimeMillis()).commit()
            val credentials = CredentialVault.read(context) ?: return finish("Secure credential storage unavailable")
            val snapshot = WifiProbe.current(context)
                ?: return finish("Wi-Fi identity unavailable; login cancelled")
            if (!LiveSessionTest.selectedWifiMatches(context)) return finish("Trusted Wi-Fi changed; login cancelled")
            val submitted = FortinetWire.submitOnce(snapshot.network, credentials.username, credentials.password)
            Thread.sleep(1700)
            val verified = WifiProbe.probe(context)
            return if (verified.status == "Online") {
                prefs.edit().putLong("last_success", System.currentTimeMillis()).apply()
                finish("Internet restored automatically")
            } else {
                finish(submitted.description + "; internet not yet verified. No immediate retry.")
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return finish("Check interrupted; no immediate retry")
        } catch (_: Exception) {
            return finish("Check could not complete; no immediate retry")
        } finally {
            running.set(false)
        }
    }
}
