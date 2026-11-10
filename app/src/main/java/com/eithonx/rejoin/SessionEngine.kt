package com.eithonx.rejoin

import android.content.Context
import android.net.Network
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

object SessionEngine {
    data class Result(val message: String, val outcome: RecoveryPolicy.Outcome, val busy: Boolean = false)
    private val running = AtomicBoolean(false)
    private const val COOLDOWN_MS = 20 * 60_000L

    fun run(
        context: Context,
        userInitiated: Boolean = false,
        explicitlyLoggedOut: Boolean = false,
        progress: (String) -> Unit = {},
        requiredNetwork: Network? = null,
        requiredProfileId: String? = null
    ): Result {
        if (!running.compareAndSet(false, true)) {
            return Result("Another connection check is running", RecoveryPolicy.Outcome.UNKNOWN, busy = true)
        }
        val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        var checkedProfileId: String? = null

        fun finish(message: String): Result {
            val outcome = RecoveryPolicy.fromMessage(message)
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
                .putString("last_profile_id", checkedProfileId)
                .putLong("last_checked", System.currentTimeMillis())
                .putString("last_class", when (outcome) {
                    RecoveryPolicy.Outcome.HEALTHY -> "healthy"
                    RecoveryPolicy.Outcome.RECOVERED -> "restored"
                    RecoveryPolicy.Outcome.PORTAL -> "portal"
                    RecoveryPolicy.Outcome.FAILED -> "failed"
                    RecoveryPolicy.Outcome.UNKNOWN -> "unknown"
                })
                .apply()
            if (event != null) ActivityHistory.record(context, event)
            return Result(message, outcome)
        }

        try {
            if (!userInitiated && !RecoveryPrefs.enabled(context))
                return finish("Automatic recovery disabled")
            if (!userInitiated && now < prefs.getLong("manual_test_until", 0L))
                return finish("Manual session test active; automatic work paused")

            val snapshot = WifiProbe.current(context, requiredNetwork)
                ?: return finish("Trusted Wi-Fi identity unavailable; check background location access")
            val trusted = TrustedNetworks.find(context, snapshot)
                ?: return finish("Not a trusted Wi-Fi; no request sent")
            if (requiredProfileId != null && trusted.id != requiredProfileId) {
                return finish("Trusted Wi-Fi changed; login cancelled")
            }
            checkedProfileId = trusted.id

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
                return finish("Sign-in permission needed")
            if (!CredentialVault.hasLoginFor(context, trusted))
                return finish("No saved login for " + trusted.label + "; open Settings")

            // Only background attempts are rate limited. User-initiated attempts
            // require an explicit tap and are always single-shot.
            if (!userInitiated && System.currentTimeMillis() <
                prefs.getLong("next_allowed_" + trusted.id, 0L))
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
            if (TrustedNetworks.find(context, current)?.id != trusted.id)
                return finish("Trusted Wi-Fi changed; login cancelled")
            val account = CredentialVault.resolve(context, trusted)
                ?: return finish("Saved login unavailable for " + trusted.label)
            if (!userInitiated) {
                if (!RecoveryPrefs.enabled(context)) return finish("Automatic recovery disabled")
                prefs.edit().putLong("next_allowed_" + trusted.id, System.currentTimeMillis() + COOLDOWN_MS)
                    .commit()
            }
            prefs.edit().putLong("last_attempt", System.currentTimeMillis())
                .putLong("login_attempts_total", prefs.getLong("login_attempts_total", 0L) + 1L)
                .apply()

            progress("Signing in")
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
                    .putLong("last_success_" + trusted.id, System.currentTimeMillis())
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
