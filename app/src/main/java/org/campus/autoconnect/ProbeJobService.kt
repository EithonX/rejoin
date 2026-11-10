package org.campus.autoconnect

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.os.Build

/**
 * Short Wi-Fi-constrained probe. Jobs persist in Android JobScheduler after
 * this process exits. Periodic job 417 is never rearmed from inside its worker.
 * This avoids the old race where recovery silently stopped after a few hours.
 */
class ProbeJobService : JobService() {
    @Volatile private var stopping = false
    @Volatile private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (!RecoveryPrefs.enabled(this)) return false
        stopping = false
        val p = getSharedPreferences("profile", Context.MODE_PRIVATE)
        val launched = System.currentTimeMillis()
        p.edit()
            .putLong("background_last_started", launched)
            .putString("background_state", "checking")
            .putInt("background_job_id", params.jobId)
            .apply()
        val thread = Thread {
            var result = "Check was interrupted"
            try {
                val network = if (Build.VERSION.SDK_INT >= 28) params.network else null
                result = SessionEngine.run(applicationContext, requiredNetwork = network)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                result = "Background check couldn't finish (" + e.javaClass.simpleName + ")"
            } finally {
                val finished = System.currentTimeMillis()
                getSharedPreferences("profile", Context.MODE_PRIVATE).edit()
                    .putLong("background_last_finished", finished)
                    .putString("background_last_result", result.take(220))
                    .putString("background_state", if (stopping) "interrupted" else "finished")
                    .apply()
                if (!stopping) {
                    // The periodic schedule is maintained by the OS, not our thread.
                    jobFinished(params, false)
                }
            }
        }
        worker = thread
        thread.name = "Rejoin-background-check"
        thread.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopping = true
        worker?.interrupt()
        worker = null
        getSharedPreferences("profile", Context.MODE_PRIVATE).edit()
            .putString("background_state", "stopped by Android")
            .putLong("background_last_stopped", System.currentTimeMillis())
            .apply()
        // Periodic job remains in the OS scheduler. Event-triggered attempts
        // can use the next periodic check; don't retry aggressively.
        return false
    }
}
