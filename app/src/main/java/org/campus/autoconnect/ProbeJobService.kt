package org.campus.autoconnect

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.os.Build

/**
 * Runs once, exits, and asks Android for the next check. No always-on process,
 * wake lock, exact alarm, WebView, or foreground service.
 */
class ProbeJobService : JobService() {
    @Volatile private var interrupted = false
    @Volatile private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (!RecoveryPrefs.enabled(this)) return false
        interrupted = false
        val thread = Thread {
            val result = try {
                val selected = if (Build.VERSION.SDK_INT >= 28) params.network else null
                SessionEngine.run(applicationContext, requiredNetwork = selected)
            } catch (_: Exception) { "Background check failed without login" }
            if (!interrupted) {
                jobFinished(params, false)
                if (RecoveryPrefs.enabled(applicationContext))
                    RecoveryScheduler.scheduleAfter(applicationContext, result)
            }
        }
        thread.name = "Rejoin-maintenance"
        worker = thread
        thread.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        interrupted = true
        worker?.interrupt()
        worker = null
        if (RecoveryPrefs.enabled(this))
            RecoveryScheduler.schedule(this, 20L * 60_000L)
        return false
    }

    // Compatibility during migration from the previous UI.
    companion object {
        fun enabled(context: Context): Boolean = RecoveryScheduler.active(context)
        fun schedule(context: Context): Boolean = RecoveryScheduler.schedule(context, 30_000L)
        fun cancel(context: Context) = RecoveryScheduler.disable(context)
    }
}
