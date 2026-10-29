package org.campus.autoconnect

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * Runs a single short-lived check roughly every 15 minutes when Android allows.
 * Work can be deferred by Doze; no permanent service or tight polling loop.
 */
class ProbeJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                SessionEngine.run(applicationContext)
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val ID = 417
        fun enabled(context: Context): Boolean {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            return js.getPendingJob(ID) != null
        }
        fun schedule(context: Context): Boolean {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val job = JobInfo.Builder(ID, ComponentName(context, ProbeJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60 * 1000L, 5 * 60 * 1000L)
                .setPersisted(true).build()
            return js.schedule(job) == JobScheduler.RESULT_SUCCESS
        }
        fun cancel(context: Context) {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            js.cancel(ID)
        }
    }
}
