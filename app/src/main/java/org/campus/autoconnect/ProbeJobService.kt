package org.campus.autoconnect

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

/**
 * Battery-conscious maintenance job. Android batches this job and may defer it
 * during Doze; it is NOT a guaranteed real-time monitor.
 */
class ProbeJobService : JobService() {
    @Volatile private var stopped = false
    @Volatile private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val prefs = getSharedPreferences("profile", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_enabled", false)) return false
        stopped = false
        val thread = Thread {
            try {
                SessionEngine.run(applicationContext)
            } finally {
                if (!stopped) jobFinished(params, false)
            }
        }
        worker = thread
        thread.name = "CampusAutoConnect-Job"
        thread.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopped = true
        worker?.interrupt()
        worker = null
        return false
    }

    companion object {
        private const val ID = 417
        fun enabled(context: Context): Boolean {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            return js.getPendingJob(ID) != null
        }

        fun schedule(context: Context): Boolean {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val builder = JobInfo.Builder(ID, ComponentName(context, ProbeJobService::class.java))
                .setPeriodic(15 * 60 * 1000L, 5 * 60 * 1000L)
                .setPersisted(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Schedule only when Wi-Fi exists, even if its login has expired.
                val req = NetworkRequest.Builder()
                    .clearCapabilities()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()
                builder.setRequiredNetwork(req)
            } else {
                builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            }
            return try {
                js.schedule(builder.build()) == JobScheduler.RESULT_SUCCESS
            } catch (_: Exception) {
                false
            }
        }

        fun cancel(context: Context) {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            js.cancel(ID)
        }
    }
}
