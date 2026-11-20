package com.eithonx.rejoin

import android.content.Context
import android.net.Network
import com.eithonx.rejoin.InternetVerifier.State
import com.eithonx.rejoin.RecoveryPolicy.Outcome
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

object SessionEngine {
    data class Result(val message: String, val outcome: Outcome, val busy: Boolean = false)

    private val running = AtomicBoolean(false)
    private const val COOLDOWN_MS = 20 * 60_000L

    fun run(
        context: Context,
        userInitiated: Boolean = false,
        explicitlyLoggedOut: Boolean = false,
        progress: (String) -> Unit = {},
        requiredNetwork: Network? = null,
        requiredProfileId: String? = null,
        onSubmitting: () -> Unit = {}
    ): Result {
        if (!running.compareAndSet(false, true)) {
            return Result("Another connection check is running", Outcome.UNKNOWN, busy = true)
        }
        val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        var checkedWifi: WifiProbe.Snapshot? = null
        var checkedProfileId: String? = null
        var state = State.UNKNOWN

        fun finish(outcome: Outcome, message: String): Result {
            val wifi = checkedWifi
            val profileId = checkedProfileId
            if (wifi != null && profileId != null && WifiProbe.current(context, wifi.network) == wifi) {
                val editor = prefs.edit()
                ConnectionObservation(state, outcome).write(editor, profileId, wifi, message)
                editor.apply()
            }
            val event = when (outcome) {
                Outcome.HEALTHY -> "Connection healthy"
                Outcome.RECOVERED -> "Internet restored"
                Outcome.REJECTED -> "Saved Wi-Fi login rejected"
                Outcome.CERTIFICATE_ERROR -> "Certificate verification failed"
                Outcome.SECURE_PORTAL -> "Android Wi-Fi sign-in needed"
                Outcome.UNSUPPORTED_PORTAL -> "Sign-in address unrecognized"
                Outcome.PORTAL_FORM_FAILED -> "Sign-in page unavailable"
                Outcome.VERIFY_FAILED -> "Sign-in couldn’t be confirmed"
                else -> null
            }
            if (event != null) ActivityHistory.record(context, event)
            return Result(message, outcome)
        }

        try {
            if (!userInitiated && !RecoveryPrefs.enabled(context))
                return finish(Outcome.UNKNOWN, "Automatic sign-in is off")
            if (!userInitiated && System.currentTimeMillis() < prefs.getLong("manual_test_until", 0L))
                return finish(Outcome.UNKNOWN, "Manual session test active")

            val snapshot = WifiProbe.current(context, requiredNetwork)
                ?: return finish(Outcome.UNKNOWN, "Wi-Fi identity unavailable")
            val trusted = TrustedNetworks.find(context, snapshot)
                ?: return finish(Outcome.UNKNOWN, "Outside saved Wi-Fi")
            if (requiredProfileId != null && trusted.id != requiredProfileId)
                return finish(Outcome.UNKNOWN, "Saved Wi-Fi changed")
            checkedWifi = snapshot
            checkedProfileId = trusted.id

            progress("Checking internet on the saved Wi-Fi")
            val initial = InternetVerifier.check(context, snapshot.network)
            state = initial.state
            val initialOutcome = ConnectionObservation.outcomeFor(state)
            if (state == State.ONLINE) {
                prefs.edit().remove("manual_test_until").apply()
                return finish(Outcome.HEALTHY, "Internet online")
            }
            if (initialOutcome.stopsAutomaticSubmission) return finish(initialOutcome, initial.details)
            if (state != State.PORTAL && !explicitlyLoggedOut) return finish(initialOutcome, initial.details)
            if (!prefs.getBoolean("http_opt_in", false))
                return finish(Outcome.PORTAL, "Sign-in permission needed")
            val account = CredentialVault.resolve(context, trusted)
                ?: return finish(Outcome.PORTAL, "No saved login for this Wi-Fi")
            if (CredentialVault.wasRejected(context, account))
                return finish(Outcome.REJECTED, "The portal rejected this saved login")

            var portalPath = initial.portalPath
            if (!userInitiated) {
                val sinceAttempt = System.currentTimeMillis() - CredentialVault.lastAttempt(context, account)
                if (sinceAttempt in 0L until COOLDOWN_MS)
                    return finish(Outcome.COOLDOWN, "Waiting for the next sign-in attempt")
                if (portalPath == null) {
                    progress("Confirming the sign-in page")
                    Thread.sleep(Random.nextLong(2_000L, 7_001L))
                    val confirmed = InternetVerifier.check(context, snapshot.network)
                    state = confirmed.state
                    if (state != State.PORTAL)
                        return finish(ConnectionObservation.outcomeFor(state), confirmed.details)
                    portalPath = confirmed.portalPath
                }
            }

            val submission = FortinetLoginV2.submitOnce(
                context, snapshot.network, account.username, account.password, progress, portalPath
            ) {
                val current = WifiProbe.current(context, snapshot.network)
                val profile = current?.let { TrustedNetworks.find(context, it) }
                val latest = profile?.let { CredentialVault.resolve(context, it) }
                val canSubmit = current == snapshot && profile?.id == trusted.id &&
                    latest != null && latest.id == account.id && latest.revision == account.revision &&
                    prefs.getBoolean("http_opt_in", false) && (userInitiated || RecoveryPrefs.enabled(context))
                if (canSubmit) {
                    CredentialVault.recordAttempt(context, account, System.currentTimeMillis())
                    onSubmitting()
                }
                canSubmit
            }
            when (submission.status) {
                FortinetLoginV2.Submission.SECURE_PORTAL -> {
                    state = State.SECURE_PORTAL
                    return finish(Outcome.SECURE_PORTAL, submission.summary)
                }
                FortinetLoginV2.Submission.UNSUPPORTED_PORTAL -> {
                    state = State.UNSUPPORTED_PORTAL
                    return finish(Outcome.UNSUPPORTED_PORTAL, submission.summary)
                }
                FortinetLoginV2.Submission.CANCELLED -> return finish(Outcome.UNKNOWN, submission.summary)
                FortinetLoginV2.Submission.FORM_UNAVAILABLE ->
                    return finish(Outcome.PORTAL_FORM_FAILED, submission.summary)
                FortinetLoginV2.Submission.REJECTED -> {
                    state = State.PORTAL
                    CredentialVault.recordRejection(context, account, rejected = true)
                    return finish(Outcome.REJECTED, submission.summary)
                }
                FortinetLoginV2.Submission.SENT -> Unit
            }

            progress("Verifying internet after sign-in")
            Thread.sleep(1_200L)
            val confirmed = InternetVerifier.check(context, snapshot.network)
            state = confirmed.state
            if (state == State.ONLINE) {
                CredentialVault.recordRejection(context, account, rejected = false)
                prefs.edit()
                    .putLong("last_success", System.currentTimeMillis())
                    .putLong("last_success_" + trusted.id, System.currentTimeMillis())
                    .remove("manual_test_until")
                    .apply()
                NetworkStatusHint.requestAfterVerifiedOnline(context)
                return finish(Outcome.RECOVERED, "Internet restored")
            }
            val confirmedOutcome = ConnectionObservation.outcomeFor(state)
            if (confirmedOutcome.stopsAutomaticSubmission) return finish(confirmedOutcome, confirmed.details)
            return finish(Outcome.VERIFY_FAILED, submission.summary + ". " + confirmed.details)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return finish(Outcome.UNKNOWN, "Check interrupted")
        } catch (e: Exception) {
            return finish(Outcome.FAILED, "Connection check failed (" + e.javaClass.simpleName + ")")
        } finally {
            running.set(false)
        }
    }
}
