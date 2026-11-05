package org.campus.autoconnect

import android.content.Context
import android.net.Network
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * Single flight, conservative recovery of a user-authorized captive-portal
 * session. Keeps the verified FortinetLoginV2 form implementation unchanged.
 */
object SessionEngine {
    private val running = AtomicBoolean(false)
    private const val COOLDOWN_MS = 20 * 60_000L

    fun run(
        context: Context,
        userInitiated: Boolean = false,
        explicitlyLoggedOut: Boolean = false,
        progress: (String) -> Unit = {},
        requiredNetwork: Network? = null
    ): String {
        if (!running.compareAndSet(false, true)) return "Another connection check is running"
        val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        fun finish(message: String): String {
            val event = when {
                message.contains("VERIFIED", true) ||
                    message.contains("restored", true) -> "Internet restored"
                message.contains("online", true) ||
                    message.contains("healthy", true) -> "Connection healthy"
                message.contains("cooldown", true) -> "Recovery on cooldown"
                message.contains("portal", true) -> "Captive portal detected"
                message.contains("trusted Wi-Fi", true) -> "Outside trusted network"
                else -> null
            }
            prefs.edit()
                .putString("last_result", message)
                .putLong("last_checked", System.currentTimeMillis())
                .putString("last_class", when (RecoveryPolicy.fromMessage(message)) {
                    RecoveryPolicy.Outcome.HEALTHY -> "healthy"
                    RecoveryPolicy.Outcome.RECOVERED -> "restored"
                    RecoveryPolicy.Outcome.PORTAL -> "portal"
                    RecoveryPolicy.Outcome.FAILED -> "failed"
                    RecoveryPolicy.Outcome.UNKNOWN -> "unknown"
                })
                .apply()
            if (event != null) ActivityHistory.record(context, event)
            return message
        }

        try {
            if (!userInitiated && !RecoveryPrefs.enabled(context))
                return finish("Automatic recovery disabled")
            if (!userInitiated && now < prefs.getLong("manual_test_until", 0L))
                return finish("Manual session test active; automatic work paused")

            val snapshot = WifiProbe.current(context, requiredNetwork)
                ?: return finish("Trusted Wi-Fi identity unavailable; check background location access")
            if (snapshot.ssid != prefs.getString("ssid", null) ||
                !snapshot.bssid.equals(prefs.getString("bssid", null), true))
                return finish("Not the trusted Wi-Fi; no request sent")

            progress("Checking Wi-Fi access")
            val initial = InternetVerifier.check(context, snapshot.network)
            if (initial.state == InternetVerifier.State.ONLINE) {
                prefs.edit().remove("manual_test_until").apply()
                return finish("Internet online; no login necessary")
            }
            if (initial.state != InternetVerifier.State.PORTAL && !explicitlyLoggedOut) {
                return finish("Network state uncertain; no password sent: " + initial.details)
            }
            if (!prefs.getBoolean("http_opt_in", false))
                return finish("CU HTTP login consent not granted")
            if (!CredentialVault.hasCredentials(context))
                return finish("Credentials not configured")

            // Only background attempts are rate limited. User-initiated attempts
            // require an explicit tap and are always single-shot.
            if (!userInitiated && System.currentTimeMillis() <
                prefs.getLong("next_allowed", 0L))
                return finish("Portal detected; retry cooldown active")
            if (!userInitiated) {
                progress("Checking whether another device has restored internet")
                Thread.sleep(Random.nextLong(2_000L, 7_001L))
                val again = InternetVerifier.check(context, snapshot.network)
                if (again.state == InternetVerifier.State.ONLINE)
                    return finish("Internet online; another device restored access")
                if (again.state != InternetVerifier.State.PORTAL)
                    return finish("Portal no longer confirmed; no password sent")
            }

            // Validate identity again immediately before submitting credentials.
            val current = WifiProbe.current(context, snapshot.network)
                ?: return finish("Trusted Wi-Fi changed; login cancelled")
            if (current.ssid != snapshot.ssid ||
                !current.bssid.equals(snapshot.bssid, true))
                return finish("Trusted Wi-Fi changed; login cancelled")
            val account = CredentialVault.read(context)
                ?: return finish("Encrypted credentials unavailable")
            if (!userInitiated) {
                prefs.edit().putLong("next_allowed", System.currentTimeMillis() + COOLDOWN_MS)
                    .commit()
            }
            prefs.edit().putLong("last_attempt", System.currentTimeMillis())
                .putLong("login_attempts_total", prefs.getLong("login_attempts_total", 0L) + 1L)
                .apply()

            progress("Authenticating with CU")
            val submitted = FortinetLoginV2.submitOnce(
                context, snapshot.network, account.username, account.password, progress
            )
            if (!submitted.attempted)
                return finish("Sign-in not submitted: " + submitted.summary)

            progress("Verifying connection")
            Thread.sleep(1_200L)
            val confirmed = InternetVerifier.check(context, snapshot.network)
            if (confirmed.state == InternetVerifier.State.ONLINE) {
                prefs.edit()
                    .putLong("last_success", System.currentTimeMillis())
                    .remove("manual_test_until")
                    .apply()
                NetworkStatusHint.requestAfterVerifiedOnline(context)
                return finish("LOGIN VERIFIED — internet restored")
            }
            return finish("Sign-in submitted; internet verification inconclusive. " +
                submitted.summary + "; " + confirmed.details + ". No automatic retry now.")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return finish("Check interrupted; no immediate retry")
        } catch (e: Exception) {
            return finish("Connection check failed (" + e.javaClass.simpleName + ")")
        } finally {
            running.set(false)
        }
    }
}
