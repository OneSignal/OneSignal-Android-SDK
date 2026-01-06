package com.onesignal

import android.app.job.JobParameters
import android.app.job.JobService

/**
 * Temporary shim for old OneSignal v4 jobs pointing to com.onesignal.SyncJobService.
 *
 * The v4 SyncJobService was used to run background sync; v5 has moved to a new architecture,
 *  but scheduled jobs from the old service can still be lingering in JobScheduler on devices
 *  that previously had v4 installed. When those old jobs fire, the system tries to instantiate
 *  com.onesignal.SyncJobService. Since the class isn’t in the v5 SDK anymore, we will get a
 *  ClassNotFoundException and a crash.
 *
 *  Providing a no-op implementation with the same fully qualified name lets those old jobs run
 *   once, do nothing, and finish, which clears them out. This is intentionally a no-op and
 *   finishes immediately.
 */
class SyncJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        // Job finishes immediately, nothing to do.
        jobFinished(params, false)
        return false // No more work on a background thread.
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Don't reschedule the job.
        return false
    }
}
