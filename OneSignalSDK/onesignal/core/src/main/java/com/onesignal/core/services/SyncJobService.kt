/**
 * Modified MIT License
 *
 * Copyright 2018 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.onesignal.core.services

import android.app.job.JobParameters
import android.app.job.JobService
import com.onesignal.OneSignal
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.core.internal.background.IBackgroundManager
import com.onesignal.debug.internal.logging.Logging

class SyncJobService : JobService() {
    override fun onStartJob(jobParameters: JobParameters): Boolean {
        suspendifyOnIO {
            var reschedule = false

            try {
                // Init OneSignal in background
                if (!OneSignal.initWithContext(this)) {
                    return@suspendifyOnIO
                }

                val backgroundService = OneSignal.getService<IBackgroundManager>()
                backgroundService.runBackgroundServices()

                Logging.debug("LollipopSyncRunnable:JobFinished needsJobReschedule: " + backgroundService.needsJobReschedule)

                // Reschedule if needed
                reschedule = backgroundService.needsJobReschedule
                backgroundService.needsJobReschedule = false
            } finally {
                // Always call jobFinished to finish the job; onStopJob will handle the case when init failed
                jobFinished(jobParameters, reschedule)
            }
        }

        // Returning true means the job will always continue running and do everything else in IO thread
        // When initWithContext failed, the background task will simply end
        return true
    }

    override fun onStopJob(jobParameters: JobParameters): Boolean {
        /*
         * After 5.4, onStartJob calls initWithContext in background. That introduced a small possibility
         * when onStopJob is called before the initialization completes in the background. When that happens,
         * OneSignal.getService will run into a NPE. In that case, we just need to omit the job and do not
         * reschedule.
         */

        // Additional hardening in the event of getService failure
        try {
            // We assume init has been called via onStartJob\
            val backgroundService = OneSignal.getService<IBackgroundManager>()
            val reschedule = backgroundService.cancelRunBackgroundServices()
            Logging.debug("SyncJobService onStopJob called, system conditions not available reschedule: $reschedule")
            return reschedule
        } catch (e: Exception) {
            Logging.error("SyncJobService onStopJob failed, omit and do not reschedule")
            return false
        }
    }
}
