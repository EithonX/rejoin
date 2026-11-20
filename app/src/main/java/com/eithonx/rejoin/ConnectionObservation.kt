package com.eithonx.rejoin

import android.content.SharedPreferences

internal data class ConnectionObservation(
    val state: InternetVerifier.State,
    val outcome: RecoveryPolicy.Outcome,
    val at: Long = System.currentTimeMillis()
) {
    fun write(
        editor: SharedPreferences.Editor,
        profileId: String,
        wifi: WifiProbe.Snapshot,
        message: String
    ) {
        editor.putString("last_result", message)
            .putString("last_profile_id", profileId)
            .putLong("last_network", wifi.network.networkHandle)
            .putString("last_bssid", wifi.bssid)
            .putString("last_wifi_state", state.name)
            .putString("last_outcome", outcome.name)
            .putLong("last_checked", at)
    }

    companion object {
        const val MAX_AGE_MS = 120_000L

        fun read(prefs: SharedPreferences, profileId: String, wifi: WifiProbe.Snapshot): ConnectionObservation? {
            if (prefs.getString("last_profile_id", null) != profileId ||
                prefs.getLong("last_network", 0L) != wifi.network.networkHandle ||
                !wifi.bssid.equals(prefs.getString("last_bssid", null), ignoreCase = true)) return null
            val at = prefs.getLong("last_checked", 0L)
            if (System.currentTimeMillis() - at !in 0L..MAX_AGE_MS) return null
            val state = InternetVerifier.State.entries.firstOrNull {
                it.name == prefs.getString("last_wifi_state", null)
            } ?: return null
            val outcome = RecoveryPolicy.Outcome.entries.firstOrNull {
                it.name == prefs.getString("last_outcome", null)
            } ?: return null
            return ConnectionObservation(state, outcome, at)
        }

        fun outcomeFor(state: InternetVerifier.State): RecoveryPolicy.Outcome = when (state) {
            InternetVerifier.State.ONLINE -> RecoveryPolicy.Outcome.HEALTHY
            InternetVerifier.State.PORTAL -> RecoveryPolicy.Outcome.PORTAL
            InternetVerifier.State.SECURE_PORTAL -> RecoveryPolicy.Outcome.SECURE_PORTAL
            InternetVerifier.State.CERTIFICATE_ERROR -> RecoveryPolicy.Outcome.CERTIFICATE_ERROR
            InternetVerifier.State.UNSUPPORTED_PORTAL -> RecoveryPolicy.Outcome.UNSUPPORTED_PORTAL
            InternetVerifier.State.UNKNOWN -> RecoveryPolicy.Outcome.UNKNOWN
        }
    }
}
