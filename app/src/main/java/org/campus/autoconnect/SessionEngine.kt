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

    fun run(context: Context, userInitiated: Boolean = false, explicitlyLoggedOut: Boolean = false, progress: (String) -> Unit = {}): String {
        if (!running.compareAndSet(false, true)) return "Another check is already running."
        val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        fun finish(message: String): String {
            prefs.edit().putString("last_result", message)
                .putLong("last_checked", System.currentTimeMillis()).apply()
            return message
        }
        try {
            if (!userInitiated && System.currentTimeMillis() <
                prefs.getLong("manual_test_until", 0L)) {
                return finish("Manual session test active; background repair temporarily paused")
            }
            // In the explicit test, we have just issued a user-approved logout.
            // Skip pre-login DNS/probe loops entirely; go straight to the actual
            // FortiGate form and verify connectivity after submitting.
            if (explicitlyLoggedOut) {
                if (!prefs.getBoolean("auto_enabled", false) || !prefs.getBoolean("http_opt_in", false))
                    return finish("Automatic login disabled or HTTP consent missing")
                if (!CredentialVault.hasCredentials(context))
                    return finish("Encrypted credentials not configured")
                progress("Stage 0/3: Checking trusted Wi-Fi")
                val snapshot = WifiProbe.current(context)
                    ?: return finish("Wi-Fi identity unavailable")
                if (!LiveSessionTest.selectedWifiMatches(context))
                    return finish("Trusted Wi-Fi mismatch; no password sent")
                val credentials = CredentialVault.read(context)
                    ?: return finish("Could not decrypt saved credentials")
                val submitted = FortinetLoginV2.submitOnce(
                    context, snapshot.network, credentials.username, credentials.password, progress
                )
                progress("Stage 3: Verifying restored internet over Wi-Fi")
                Thread.sleep(1000L)
                val verified = InternetVerifier.check(context)
                if (verified.state == InternetVerifier.State.ONLINE) {
                    prefs.edit().putLong("last_success", System.currentTimeMillis()).apply()
                    return finish("LOGIN VERIFIED: " + verified.details)
                }
                return finish(submitted.summary + "; verification: " +
                    verified.details + ". This is not proof the login was rejected.")
            }

            val state = WifiProbe.probe(context)
            if (!state.matched) return finish(state.status + "; no action")
            if (state.status == "Online") return finish("Online — no authentication needed")
            val publicCheck = InternetVerifier.check(context)
            if (publicCheck.state == InternetVerifier.State.ONLINE)
                return finish("Internet online; no login: " + publicCheck.details)
            if (state.status != "Captive portal detected" &&
                publicCheck.state != InternetVerifier.State.PORTAL)
                return finish("Portal unconfirmed: " + publicCheck.details + "; no password sent")
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
                Thread.sleep(Random.nextLong(2000, 10001))
            } else {
                Thread.sleep(900)
            }
            val again = InternetVerifier.check(context)
            if (again.state == InternetVerifier.State.ONLINE)
                return finish("Another device restored internet; no login needed")
            if (again.state != InternetVerifier.State.PORTAL)
                return finish("Portal no longer confirmed: " + again.details + "; no login sent")

            // Persist before POST, so a killed process will not cause a login storm.
            prefs.edit().putLong("next_allowed", System.currentTimeMillis() + COOLDOWN_MS)
                .putLong("last_attempt", System.currentTimeMillis()).commit()
            val credentials = CredentialVault.read(context) ?: return finish("Secure credential storage unavailable")
            val snapshot = WifiProbe.current(context)
                ?: return finish("Wi-Fi identity unavailable; login cancelled")
            if (!LiveSessionTest.selectedWifiMatches(context)) return finish("Trusted Wi-Fi changed; login cancelled")
            val submitted = FortinetLoginV2.submitOnce(context, snapshot.network, credentials.username, credentials.password, progress)
            progress("Stage 3/3: Verifying internet (no DNS lookup)")
            Thread.sleep(1000)
            val verified = InternetVerifier.check(context)
            return if (verified.state == InternetVerifier.State.ONLINE) {
                prefs.edit().putLong("last_success", System.currentTimeMillis()).apply()
                finish("Internet verified automatically: " + verified.details)
            } else {
                finish(submitted.summary + "; verification: " + verified.details +
                    "; no immediate retry.")
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
