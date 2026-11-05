package org.campus.autoconnect

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlin.random.Random

/**
 * One-shot, self-rescheduling Android-managed work. No ongoing loop or service.
 * A PendingIntent callback additionally wakes the scheduler for new Wi-Fi networks.
 * Neither path can observe a silent campus firewall expiry without a verification probe.
 */
object RecoveryScheduler {
    const val JOB_ID = 417
    private const val ACTION_NETWORK = "org.campus.autoconnect.ACTION_WIFI_AVAILABLE"

    fun active(context: Context): Boolean {
        val manager = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        return manager.getPendingJob(JOB_ID) != null
    }

    private fun wifiRequest(): NetworkRequest = NetworkRequest.Builder()
        .clearCapabilities()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()

    fun schedule(context: Context, delayMs: Long): Boolean {
        if (!RecoveryPrefs.enabled(context)) return false
        val manager = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        // A little jitter spreads work across many installations on one campus.
        val jitter = Random.nextLong(0L, 40001L)
        val delay = (delayMs + jitter).coerceAtLeast(5000L)
        val builder = JobInfo.Builder(JOB_ID, ComponentName(context, ProbeJobService::class.java))
            .setMinimumLatency(delay)
            .setOverrideDeadline(delay + 10L * 60L * 1000L)
            .setPersisted(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setRequiredNetwork(wifiRequest())
        } else {
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        }
        return try {
            if (manager.schedule(builder.build()) == JobScheduler.RESULT_SUCCESS) {
                context.getSharedPreferences("profile", Context.MODE_PRIVATE).edit()
                    .putLong("next_check_at", System.currentTimeMillis() + delay)
                    .apply()
                true
            } else false
        } catch (_: Exception) { false }
    }

    fun scheduleAfter(context: Context, result: String): Boolean {
        val mins = RecoveryPolicy.minutes(RecoveryPrefs.mode(context), RecoveryPolicy.fromMessage(result))
        return schedule(context, mins.toLong() * 60_000L)
    }

    fun onWifiAppeared(context: Context) {
        if (!RecoveryPrefs.enabled(context)) return
        val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_wifi_event", 0L) in 0..119999L) return
        prefs.edit().putLong("last_wifi_event", now).apply()
        schedule(context, 10_000L)
    }

    private fun wifiPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NetworkWakeReceiver::class.java).apply { action = ACTION_NETWORK }
        return PendingIntent.getBroadcast(
            context, 81, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun registerWifiEvents(context: Context): Boolean {
        if (!RecoveryPrefs.enabled(context)) return false
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            manager.registerNetworkCallback(wifiRequest(), wifiPendingIntent(context))
            true
        } catch (_: Exception) { false }
    }

    fun disable(context: Context) {
        val manager = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        manager.cancel(JOB_ID)
        try {
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivity.unregisterNetworkCallback(wifiPendingIntent(context))
        } catch (_: Exception) {}
        context.getSharedPreferences("profile", Context.MODE_PRIVATE).edit()
            .remove("next_check_at").apply()
    }

    fun initialize(context: Context) {
        if (!RecoveryPrefs.enabled(context)) return
        registerWifiEvents(context)
        schedule(context, 45_000L)
    }
}

class NetworkWakeReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "org.campus.autoconnect.ACTION_WIFI_AVAILABLE") {
            RecoveryScheduler.onWifiAppeared(context.applicationContext)
        }
    }
}

class RestoreAfterBootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            RecoveryScheduler.initialize(context.applicationContext)
        }
    }
}
