package org.campus.autoconnect

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

class ProbeJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            val result = WifiProbe.probe(applicationContext)
            if (result.matched) {
                getSharedPreferences("profile", Context.MODE_PRIVATE).edit()
                    .putString("last_state", result.status)
                    .putLong("last_checked", System.currentTimeMillis()).apply()
            }
            jobFinished(params, false)
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
        fun schedule(context: Context) {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val job = JobInfo.Builder(ID, ComponentName(context, ProbeJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(30 * 60 * 1000L)
                .setPersisted(true).build()
            js.schedule(job)
        }
        fun cancel(context: Context) {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            js.cancel(ID)
        }
    }
}
