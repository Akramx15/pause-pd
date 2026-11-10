package dev.pausepd

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * Safety net for a stale request: the kernel keeps `user_mode` after unplug and
 * re-arms on the next PPS charger, so a periodic job cancels it when no PPS
 * source is attached. 15 min is the JobScheduler floor; the in-app receiver and
 * the game-watch service cancel immediately while they are alive.
 */
class ResetJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                val st = PassThrough.read()
                if (st.requested && !st.hasPps) PassThrough.set(false)
            } catch (_: Throwable) {
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val ID = 41

        fun schedule(c: Context) {
            val js = c.getSystemService(JobScheduler::class.java) ?: return
            if (js.getPendingJob(ID) != null) return
            val job = JobInfo.Builder(ID, ComponentName(c, ResetJobService::class.java))
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build()
            runCatching { js.schedule(job) }
        }
    }
}
