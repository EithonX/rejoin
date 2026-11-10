package com.eithonx.rejoin

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

/**
 * Process-independent background recovery.
 *
 * Android's captive portal signal triggers an early check. The persisted
 * periodic job covers portals Android misses, including expiry on the same Wi-Fi.
 */
object RecoveryScheduler {
    const val JOB_ID = 417
    const val EVENT_JOB_ID = 418
    internal const val ACTION_NETWORK = "com.eithonx.rejoin.ACTION_WIFI_AVAILABLE"
    private const val PROFILE = "profile"

    private fun scheduler(context: Context): JobScheduler =
        context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    internal fun wifiRequest(captiveOnly: Boolean = false): NetworkRequest = NetworkRequest.Builder().apply {
        if (Build.VERSION.SDK_INT >= 30) clearCapabilities()
        else {
            removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
            removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }
        addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        if (captiveOnly) addCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
    }.build()

    private fun wifiConstraint(builder: JobInfo.Builder): JobInfo.Builder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            builder.setRequiredNetwork(wifiRequest())
        else builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        return builder
    }

    private fun periodAndFlex(context: Context): Pair<Long, Long> =
        if (RecoveryPrefs.mode(context) == RecoveryPolicy.Mode.RESPONSIVE)
            Pair(15L * 60_000L, 5L * 60_000L)
        else Pair(30L * 60_000L, 10L * 60_000L)

    private fun JobScheduler.periodicJob(): JobInfo? =
        if (Build.VERSION.SDK_INT >= 24) getPendingJob(JOB_ID)
        else allPendingJobs.firstOrNull { it.id == JOB_ID }

    fun active(context: Context): Boolean = scheduler(context).periodicJob() != null

    fun schedulePeriodic(context: Context): Boolean {
        if (!RecoveryPrefs.enabled(context)) return false
        val (period, flex) = periodAndFlex(context)
        val job = wifiConstraint(JobInfo.Builder(
            JOB_ID, ComponentName(context, ProbeJobService::class.java)
        )).apply {
            if (Build.VERSION.SDK_INT >= 24) setPeriodic(period, flex)
            else setPeriodic(period)
            setPersisted(true)
        }.build()
        return try {
            val manager = scheduler(context)
            val old = manager.periodicJob()
            // Replacing an identical periodic job unnecessarily resets its cadence.
            if (old != null && old.isPeriodic && old.intervalMillis == period) return true
            val ok = manager.schedule(job) == JobScheduler.RESULT_SUCCESS
            if (ok) context.getSharedPreferences(PROFILE, Context.MODE_PRIVATE).edit()
                .putLong("schedule_registered_at", System.currentTimeMillis()).apply()
            ok
        } catch (_: Exception) { false }
    }

    fun scheduleEvent(context: Context, delayMs: Long = 15_000L): Boolean {
        if (!RecoveryPrefs.enabled(context) || eventCandidate(context) == null) return false
        val delay = delayMs.coerceIn(5_000L, 2L * 60_000L)
        val builder = wifiConstraint(JobInfo.Builder(
            EVENT_JOB_ID, ComponentName(context, ProbeJobService::class.java)
        )).setMinimumLatency(delay)
        return try {
            scheduler(context).schedule(builder.build()) == JobScheduler.RESULT_SUCCESS
        } catch (_: Exception) { false }
    }

    fun schedule(context: Context, delayMs: Long): Boolean {
        val periodic = schedulePeriodic(context)
        if (periodic) scheduleEvent(context, delayMs)
        return periodic
    }

    private fun wifiIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 81,
            Intent(context, NetworkWakeReceiver::class.java).apply { action = ACTION_NETWORK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun registerWifiEvents(context: Context): Boolean {
        if (!RecoveryPrefs.enabled(context)) return false
        return try {
            (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .registerNetworkCallback(wifiRequest(captiveOnly = true), wifiIntent(context))
            true
        } catch (_: Exception) { false }
    }

    internal fun eventCandidate(context: Context, network: Network? = null): WifiProbe.Snapshot? {
        val prefs = context.getSharedPreferences(PROFILE, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("http_opt_in", false)) return null
        val wifi = WifiProbe.current(context, network) ?: return null
        val profile = TrustedNetworks.find(context, wifi) ?: return null
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val caps = manager.getNetworkCapabilities(wifi.network) ?: return null
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) return null
        return wifi.takeIf { CredentialVault.hasLoginFor(context, profile) }
    }

    fun onPortalDetected(context: Context) {
        if (!RecoveryPrefs.enabled(context)) return
        val prefs = context.getSharedPreferences(PROFILE, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val previous = prefs.getLong("last_wifi_event", 0L)
        if (previous > 0L && now - previous in 0L..119_999L) return
        if (scheduleEvent(context)) prefs.edit().putLong("last_wifi_event", now).apply()
    }

    internal fun rearmAfterCheck(context: Context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        // Android sends a PendingIntent once. Rearming during the same captive
        // session would immediately trigger another wakeup.
        val captiveWifi = manager.allNetworks.any { network ->
            val caps = manager.getNetworkCapabilities(network) ?: return@any false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        }
        if (!captiveWifi) registerWifiEvents(context)
    }

    fun disable(context: Context) {
        val manager = scheduler(context)
        manager.cancel(JOB_ID)
        manager.cancel(EVENT_JOB_ID)
        try {
            (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .unregisterNetworkCallback(wifiIntent(context))
        } catch (_: Exception) {}
        context.getSharedPreferences(PROFILE, Context.MODE_PRIVATE).edit()
            .remove("next_check_at").apply()
    }

    fun initialize(context: Context) {
        if (!RecoveryPrefs.enabled(context)) return
        registerWifiEvents(context)
        schedulePeriodic(context)
        // A boot/update is also an opportunity for a bounded early check.
        scheduleEvent(context, 60_000L)
    }
}

class NetworkWakeReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RecoveryScheduler.ACTION_NETWORK)
            RecoveryScheduler.onPortalDetected(context.applicationContext)
    }
}

class RestoreAfterBootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED)
            RecoveryScheduler.initialize(context.applicationContext)
    }
}
