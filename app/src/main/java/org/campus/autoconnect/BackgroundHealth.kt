package org.campus.autoconnect

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager

/**
 * Human-readable background readiness.
 * No tracking, remote diagnostics, or special device privileges.
 */
object BackgroundHealth {
    enum class Level { READY, NEEDS_ACTION, PAUSED }

    data class State(
        val level: Level,
        val title: String,
        val description: String,
        val lastRun: Long,
        val lastResult: String,
        val hasJob: Boolean,
        val backgroundLocation: Boolean,
        val restricted: Boolean,
        val batteryUnrestricted: Boolean
    )

    fun inspect(context: Context): State {
        val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val job = RecoveryScheduler.active(context)
        val lastRun = prefs.getLong("background_last_finished", 0L)
        val result = prefs.getString("background_last_result", "").orEmpty()
        val foregroundLocation =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val backgroundLocation = Build.VERSION.SDK_INT < 29 ||
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val restricted = if (Build.VERSION.SDK_INT >= 28) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.isBackgroundRestricted
        } else false
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val unrestricted = power.isIgnoringBatteryOptimizations(context.packageName)

        if (!RecoveryPrefs.enabled(context))
            return State(Level.PAUSED, "Automatic recovery is off",
                "Turn it on when you're ready.", lastRun, result, job,
                backgroundLocation, restricted, unrestricted)

        val title: String
        val details: String
        val level: Level
        when {
            !foregroundLocation -> {
                level = Level.NEEDS_ACTION
                title = "Allow Wi-Fi identification"
                details = "Rejoin needs Location access to recognize your saved router."
            }
            !WifiProbe.locationEnabled(context) -> {
                level = Level.NEEDS_ACTION
                title = "Location is off"
                details = "Turn on Location so Rejoin can recognize your saved Wi-Fi."
            }
            !backgroundLocation -> {
                level = Level.NEEDS_ACTION
                title = "Allow background Wi-Fi access"
                details = "Choose 'Allow all the time' for Location so Rejoin can identify Wi-Fi while closed."
            }
            restricted -> {
                level = Level.NEEDS_ACTION
                title = "Android is restricting Rejoin"
                details = "Allow background activity for Rejoin in your phone's app settings."
            }
            !job -> {
                level = Level.NEEDS_ACTION
                title = "Background checks need restarting"
                details = "Restart background checks so Android can schedule them again."
            }
            !unrestricted -> {
                level = Level.NEEDS_ACTION
                title = "Background access may be delayed"
                details = "For more reliable recovery, allow Rejoin to run in the background."
            }
            else -> {
                level = Level.READY
                title = "Background protection is ready"
                details = "Android has a scheduled check. Timing may still vary while the phone sleeps."
            }
        }
        return State(level, title, details, lastRun, result, job,
            backgroundLocation, restricted, unrestricted)
    }
}
