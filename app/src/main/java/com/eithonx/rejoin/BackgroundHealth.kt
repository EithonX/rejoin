package com.eithonx.rejoin

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager

object BackgroundHealth {
    enum class Issue(val title: String, val detail: String, val action: String, val required: Boolean = true) {
        PRECISE_LOCATION("Precise location needed", "Allow precise location so Rejoin can identify your saved Wi-Fi.", "Review permissions"),
        LOCATION_OFF("Location is off", "Turn on Location to identify your Wi-Fi.", "Open Location settings"),
        BACKGROUND_LOCATION("Allow location all the time",
            "In Permissions → Location, choose “Allow all the time” for sign-in when Rejoin is closed.", "Review permissions"),
        RESTRICTED("Background activity is restricted",
            "Allow background activity so Android can run sign-in checks.", "Open app settings"),
        NO_JOB("Background checks are paused", "Restart the scheduled checks.", "Restart checks"),
        OPTIMIZED("Checks may run later", "Android can delay checks to save battery.", "Open app settings", required = false)
    }

    data class State(val enabled: Boolean, val issue: Issue?, val lastRun: Long)

    fun inspect(context: Context): State {
        val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val lastRun = prefs.getLong("background_last_finished", 0L)
        if (!RecoveryPrefs.enabled(context)) return State(false, null, lastRun)
        val issue = when {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED -> Issue.PRECISE_LOCATION
            !WifiProbe.locationEnabled(context) -> Issue.LOCATION_OFF
            Build.VERSION.SDK_INT >= 29 &&
                context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
                PackageManager.PERMISSION_GRANTED -> Issue.BACKGROUND_LOCATION
            Build.VERSION.SDK_INT >= 28 &&
                context.getSystemService(ActivityManager::class.java).isBackgroundRestricted -> Issue.RESTRICTED
            !RecoveryScheduler.active(context) -> Issue.NO_JOB
            !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) ->
                Issue.OPTIMIZED
            else -> null
        }
        return State(true, issue, lastRun)
    }
}
