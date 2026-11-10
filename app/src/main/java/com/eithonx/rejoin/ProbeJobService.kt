package com.eithonx.rejoin

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import android.os.Handler
import android.os.Looper

class ProbeJobService : JobService() {
    private val jobs = mutableMapOf<Int, Thread>()
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("profile", MODE_PRIVATE) }

    override fun onStartJob(params: JobParameters): Boolean {
        if (!RecoveryPrefs.enabled(this) || jobs.containsKey(params.jobId)) return false
        prefs.edit()
            .putLong("background_last_started", System.currentTimeMillis())
            .putString("background_state", "checking")
            .apply()

        val thread = Thread({
            val result = try {
                val network = if (Build.VERSION.SDK_INT >= 28) params.network else null
                if (params.jobId == RecoveryScheduler.EVENT_JOB_ID) {
                    val wifi = RecoveryScheduler.eventCandidate(applicationContext, network)
                    wifi?.let { SessionEngine.run(applicationContext, requiredNetwork = it.network) }
                } else {
                    SessionEngine.run(applicationContext, requiredNetwork = network)
                }
            } catch (_: RuntimeException) {
                SessionEngine.Result("Background check couldn’t finish", RecoveryPolicy.Outcome.FAILED)
            }
            val completed = Thread.currentThread()
            main.post {
                if (jobs[params.jobId] !== completed) return@post
                jobs.remove(params.jobId)
                val update = prefs.edit().putString("background_state", "finished")
                if (result != null && !result.busy) {
                    update.putLong("background_last_finished", System.currentTimeMillis())
                        .putString("background_last_result", result.message.take(220))
                        .putInt("background_job_id", params.jobId)
                }
                update.apply()
                RecoveryScheduler.rearmAfterCheck(applicationContext)
                jobFinished(params, false)
            }
        }, "Rejoin-background-check")
        jobs[params.jobId] = thread
        thread.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        jobs.remove(params.jobId)?.interrupt()
        if (jobs.isEmpty()) {
            prefs.edit()
                .putString("background_state", "stopped by Android")
                .putLong("background_last_stopped", System.currentTimeMillis())
                .apply()
        }
        return false
    }

    override fun onDestroy() {
        jobs.values.forEach(Thread::interrupt)
        jobs.clear()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
