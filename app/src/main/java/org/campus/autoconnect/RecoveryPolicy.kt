package org.campus.autoconnect

import android.content.Context

/** Deterministic policies; testable without Android framework dependencies. */
object RecoveryPolicy {
    enum class Mode { BALANCED, RESPONSIVE }
    enum class Outcome { HEALTHY, RECOVERED, PORTAL, UNKNOWN, FAILED }

    fun minutes(mode: Mode, outcome: Outcome): Int = when (mode) {
        Mode.BALANCED -> when (outcome) {
            Outcome.HEALTHY -> 30
            Outcome.RECOVERED -> 25
            Outcome.PORTAL -> 15
            Outcome.UNKNOWN -> 20
            Outcome.FAILED -> 30
        }
        Mode.RESPONSIVE -> when (outcome) {
            Outcome.HEALTHY -> 15
            Outcome.RECOVERED -> 15
            Outcome.PORTAL -> 15
            Outcome.UNKNOWN -> 15
            Outcome.FAILED -> 20
        }
    }

    fun fromMessage(message: String): Outcome {
        val text = message.lowercase()
        return when {
            text.contains("login verified") || text.contains("verified automatically") ||
                text.contains("restored") -> Outcome.RECOVERED
            text.contains("already online") || text.contains("internet online") ||
                text.contains("no authentication needed") || text.startsWith("online") ||
                text.contains("connected") && !text.contains("not connected") -> Outcome.HEALTHY
            text.contains("portal detected") || text.contains("sign-in required") -> Outcome.PORTAL
            text.contains("failed") || text.contains("retry cooldown") ||
                text.contains("not yet verified") -> Outcome.FAILED
            else -> Outcome.UNKNOWN
        }
    }

    fun selectedMode(text: String?): Mode =
        if (text.equals("responsive", true)) Mode.RESPONSIVE else Mode.BALANCED
}

object RecoveryPrefs {
    fun enabled(context: Context): Boolean = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        .getBoolean("auto_enabled", false)
    fun mode(context: Context): RecoveryPolicy.Mode = RecoveryPolicy.selectedMode(
        context.getSharedPreferences("profile", Context.MODE_PRIVATE).getString("recovery_mode", "balanced")
    )
}
